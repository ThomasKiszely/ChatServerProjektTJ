package sample.client;

import sample.domain.User;
import sample.service.UserService;

public class AddUser {
    public static void main(String[] args) {
        String username = "";
        String password = "";
        UserService userService = new UserService();
        User user = new User(username, password, null);
        userService.register(user);
    }
}
