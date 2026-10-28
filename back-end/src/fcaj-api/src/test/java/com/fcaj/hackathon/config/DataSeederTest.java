package com.fcaj.hackathon.config;

import com.fcaj.hackathon.entity.Role;
import com.fcaj.hackathon.entity.ScoringCriteria;
import com.fcaj.hackathon.entity.ScoringCriteriaTemplate;
import com.fcaj.hackathon.repository.RoleRepository;
import com.fcaj.hackathon.repository.ScoringCriteriaRepository;
import com.fcaj.hackathon.repository.ScoringCriteriaTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {
    @Mock private RoleRepository roles;
    @Mock private ScoringCriteriaTemplateRepository templates;
    @Mock private ScoringCriteriaRepository criteria;
    @InjectMocks private DataSeeder seeder;

    @Test
    void freshDatabaseReceivesRolesAndDefaultRubricWithoutAnAdminDependency() {
        when(templates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roles, times(4)).save(roleCaptor.capture());
        assertEquals(java.util.Set.of("SYSTEM_ADMIN", "EVENT_COORDINATOR", "MENTOR", "JUDGE"),
                roleCaptor.getAllValues().stream().map(Role::getRoleName).collect(java.util.stream.Collectors.toSet()));
        ArgumentCaptor<ScoringCriteria> criteriaCaptor = ArgumentCaptor.forClass(ScoringCriteria.class);
        verify(criteria, times(5)).save(criteriaCaptor.capture());
        assertTrue(criteriaCaptor.getAllValues().stream().allMatch(item -> item.getTemplate().getIsDefault()));
    }

    @Test
    void restartLeavesExistingReferenceDataUntouched() {
        when(roles.existsByRoleName(anyString())).thenReturn(true);
        when(templates.findFirstByIsDefaultTrue()).thenReturn(Optional.of(new ScoringCriteriaTemplate()));

        seeder.run();

        verify(roles, never()).save(any());
        verify(templates, never()).save(any());
        verifyNoInteractions(criteria);
    }
}
