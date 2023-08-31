package org.pikalovanna.congratulationsbot.service;

import org.pikalovanna.congratulationsbot.entity.User;
import org.pikalovanna.congratulationsbot.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private final UsersRepository usersRepository;

    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public void saveUser(User user) {
        usersRepository.save(user);
    }

    public void deleteUser(User user) { usersRepository.delete(user);}
}
