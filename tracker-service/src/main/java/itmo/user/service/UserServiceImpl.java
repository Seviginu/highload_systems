package itmo.user.service;

import itmo.common.exception.ConflictException;
import itmo.common.exception.ResourceNotFoundException;
import itmo.user.dto.CreateUserRequest;
import itmo.user.dto.UpdateUserRequest;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.mapper.UserMapper;
import itmo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static itmo.common.persistence.ConstraintViolationDetector.isViolationOf;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String normalizedEmail = userMapper.normalizeEmail(request.email());
        ensureEmailAvailable(normalizedEmail);

        try {
            return userMapper.toResponse(userRepository.saveAndFlush(userMapper.toEntity(request)));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_users_email_lower")) {
                throw emailConflict(normalizedEmail);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public User requireEntity(Long id) {
        return findEntity(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findEntity(id);
        String normalizedEmail = userMapper.normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw emailConflict(normalizedEmail);
        }

        userMapper.updateEntity(user, request);
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "uq_users_email_lower")) {
                throw emailConflict(normalizedEmail);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findEntity(id);
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isViolationOf(exception, "fk_tasks_author", "fk_tasks_assignee")) {
                throw new ConflictException("User is referenced by other records and cannot be deleted");
            }
            throw exception;
        }
    }

    private User findEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailConflict(email);
        }
    }

    private ConflictException emailConflict(String email) {
        return new ConflictException("User with email '%s' already exists".formatted(email));
    }
}
