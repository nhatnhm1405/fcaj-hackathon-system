package com.fcaj.hackathon.storage;

import com.fcaj.hackathon.entity.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.boot.*;
import org.hibernate.boot.registry.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

class FileReferenceConcurrencyTest {
    static org.hibernate.SessionFactory factory;
    static org.hibernate.service.ServiceRegistry registry;
    @BeforeAll static void open() throws Exception {
        var settings=new HashMap<String,Object>();
        String mysql=System.getenv("FCAJ_AI_TEST_JDBC_URL");
        if(mysql!=null && !mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fcaj_ai_integration_lab\\?.*")) throw new IllegalStateException();
        settings.put("hibernate.connection.url",mysql==null?"jdbc:h2:mem:file_races;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE":mysql);
        settings.put("hibernate.connection.username",mysql==null?"sa":"root");
        settings.put("hibernate.connection.password",mysql==null?"":System.getenv("MYSQL_ROOT_PASSWORD"));
        settings.put("hibernate.hbm2ddl.auto",mysql==null?"create-drop":"validate");
        registry=new StandardServiceRegistryBuilder().applySettings(settings).build();
        var sources=new MetadataSources(registry);
        try(var files=Files.list(Path.of("src/main/java/com/fcaj/hackathon/entity"))) {
            for(var p:files.filter(p->p.toString().endsWith(".java")).toList()) sources.addAnnotatedClass(Class.forName("com.fcaj.hackathon.entity."+p.getFileName().toString().replace(".java","")));
        }
        factory=sources.buildMetadata().buildSessionFactory();
    }
    @AfterAll static void close(){if(factory!=null)factory.close();if(registry!=null)StandardServiceRegistryBuilder.destroy(registry);}
    @ParameterizedTest @ValueSource(strings={"profile-avatar","avatar-remove","avatar-avatar","release-replace","rename-replace","remove-replace"})
    void losingWriterCannotRestoreDeletedReference(String scenario) throws Exception {
        var root=Files.createTempDirectory(Path.of("target"),"race-");var storage=new LocalObjectStorage(root.toString());
        var avatars=new AvatarStorage(storage,root.toString(),"local");
        var problems=new com.fcaj.hackathon.service.TrackProblemService(null,null,null,null,storage,null);
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10};
        boolean user=scenario.contains("avatar");
        String old=user?avatars.upload(new MockMultipartFile("f","x.png","image/png",png)):"problems/"+UUID.randomUUID()+".pdf";
        String winner=user?avatars.upload(new MockMultipartFile("f","x.png","image/png",png)):"problems/"+UUID.randomUUID()+".pdf";
        String loser=user?avatars.upload(new MockMultipartFile("f","x.png","image/png",png)):"problems/"+UUID.randomUUID()+".pdf";
        if(!user){storage.put(old,new byte[]{1},"application/pdf");storage.put(winner,new byte[]{2},"application/pdf");storage.put(loser,new byte[]{3},"application/pdf");}
        Integer id;
        try(var s=factory.openSession()){var tx=s.beginTransaction();
            if(user){var u=User.builder().email(UUID.randomUUID()+"@test.invalid").fullName("Before").userType("STAFF").avatarUrl(old).build();s.persist(u);id=u.getUserId();}
            else {var now=java.time.LocalDateTime.now();var event=HackathonEvent.builder().name("Race").year(2026).season("FALL").status("IN_PROGRESS").startDate(now).endDate(now.plusDays(1)).build();s.persist(event);var t=Track.builder().event(event).name("Race").problemStorageKey(old).build();s.persist(t);id=t.getTrackId();}
            tx.commit();}
        var loaded=new CountDownLatch(1);var committed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var losing=pool.submit(()->{
                try(var s=factory.openSession()) {var tx=s.beginTransaction();Object stale=user?s.find(User.class,id):s.find(Track.class,id);loaded.countDown();
                    if(!committed.await(5,TimeUnit.SECONDS))throw new IllegalStateException("winner did not commit");
                    if(user){var u=(User)stale;if(scenario.startsWith("profile"))u.setFullName("Losing profile");else u.setAvatarUrl(scenario.equals("avatar-remove")?null:loser);}
                    else {var t=(Track)stale;if(scenario.startsWith("release"))t.setProblemReleased(true);else if(scenario.startsWith("rename"))t.setName("Losing rename");else t.setProblemStorageKey(null);}
                    TransactionSynchronizationManager.initSynchronization();
                    try {
                        if(user) avatars.replaced(old,loser);
                        else org.springframework.test.util.ReflectionTestUtils.invokeMethod(problems,"deleteAfterCompletion",old,loser);
                        assertThatThrownBy(tx::commit).isInstanceOf(jakarta.persistence.OptimisticLockException.class);if(tx.isActive())tx.rollback();
                        for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                    } finally {TransactionSynchronizationManager.clearSynchronization();}
                } catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            });
            assertThat(loaded.await(5,TimeUnit.SECONDS)).isTrue();
            try(var s=factory.openSession()){var tx=s.beginTransaction();
                if(user){var u=s.find(User.class,id);TransactionSynchronizationManager.initSynchronization();try {avatars.replaced(old,winner);u.setAvatarUrl(winner);tx.commit();for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);}finally{TransactionSynchronizationManager.clearSynchronization();}}
                else {TransactionSynchronizationManager.initSynchronization();try {
                    org.springframework.test.util.ReflectionTestUtils.invokeMethod(problems,"deleteAfterCompletion",old,winner);
                    s.find(Track.class,id).setProblemStorageKey(winner);tx.commit();
                    for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                } finally {TransactionSynchronizationManager.clearSynchronization();}}
            } finally{committed.countDown();}
            losing.get(10,TimeUnit.SECONDS);
            try(var s=factory.openSession()){assertThat(user?s.find(User.class,id).getAvatarUrl():s.find(Track.class,id).getProblemStorageKey()).isEqualTo(winner);}
            if(user)assertThat(avatars.read(winner.substring("/uploads/avatars/".length())).resource().getByteArray()).isEqualTo(png);
            else assertThat(storage.read(winner,null,10)).containsExactly((byte)2);
            String loserKey=user?"avatars/"+loser.substring("/uploads/avatars/".length()):loser;
            String oldKey=user?"avatars/"+old.substring("/uploads/avatars/".length()):old;
            assertThat(Files.exists(root.resolve(loserKey))).isFalse();
            assertThat(Files.exists(root.resolve(oldKey))).isFalse();
        }finally{pool.shutdownNow();}
    }
}
