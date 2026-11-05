package itmo.user.adapter.in.web;

import itmo.user.application.model.UserView;

public class UserMapper {
    public static UserResponse toResponse(UserView user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.role(), user.createdAt(), user.updatedAt());
    }
}
