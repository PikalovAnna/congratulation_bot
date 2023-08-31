package org.pikalovanna.congratulationsbot.entity;

import lombok.Getter;
import lombok.Setter;
import org.pikalovanna.congratulationsbot.enums.ActionStatus;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    private String username;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "action_status")
    @Enumerated(EnumType.STRING)
    ActionStatus actionStatus;
}