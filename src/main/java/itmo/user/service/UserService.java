package itmo.user.service;

import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse findById(Long id);

    User requireEntity(Long id);

    Page<UserResponse> findAll(Pageable pageable);

    UserResponse update(Long id, UpdateUserRequest request);

    void delete(Long id);
}
