package sample.service;

import org.mindrot.jbcrypt.BCrypt;
import sample.domain.User;
import sample.proto.UserRepo;

public class UserService {

    private UserRepo userRepo = new UserRepo();
    public boolean register(User user) {
        if (userRepo.findByUsername(user.getUsername()) != null) {
            return false;
        }
        return userRepo.registerUser(user);
    }
    public User login(String username, String password) {
        User user = userRepo.findByUsername(username);
        if(user != null && BCrypt.checkpw(password, user.getPassword())) {
            return user;
        }
        return null;
    }
}
