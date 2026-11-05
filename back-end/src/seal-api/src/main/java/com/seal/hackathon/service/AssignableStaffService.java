package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignableStaffService {

    private final UserRepository userRepository;

    /**
     * Danh sách STAFF đã được duyệt, để Coordinator chọn người phân công làm
     * Judge/Mentor. Loại trừ SYSTEM_ADMIN/EVENT_COORDINATOR (họ cũng là
     * userType=STAFF nhưng không được gán làm judge/mentor). Việc tạo tài khoản
     * và cấp role là của Admin (/api/admin); đây chỉ là danh sách tra cứu read-only.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listApprovedStaff() {
        return userRepository.findAssignableStaff().stream()
                .map(u -> UserResponse.builder()
                        .userId(u.getUserId())
                        .email(u.getEmail())
                        .fullName(u.getFullName())
                        .userType(u.getUserType())
                        .judgeType(u.getJudgeType())
                        .isApproved(u.getIsApproved())
                        .isActive(u.getIsActive())
                        .build())
                .collect(Collectors.toList());
    }
}
