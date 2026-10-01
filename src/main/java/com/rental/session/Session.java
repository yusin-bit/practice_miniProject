package main.java.com.rental.session;

import main.java.com.rental.user.dto.UserResponse;

public class Session {

    private static final Session instance = new Session();

    private UserResponse loginUser;

    private Session() {
    }

    public static Session getInstance() {
        return instance;
    }

    public UserResponse getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(UserResponse loginUser) {
        this.loginUser = loginUser;
    }

    public void logout() {
        this.loginUser = null;
    }
}
	
	
