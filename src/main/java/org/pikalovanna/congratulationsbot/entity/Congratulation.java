package org.pikalovanna.congratulationsbot.entity;

import lombok.Getter;
import lombok.Setter;
import org.pikalovanna.congratulationsbot.enums.ActionStatus;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name="congratulation")
public class Congratulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String text;

    @Column(name = "sticker_id")
    private String stickerId;

    @Column(name = "date_send")
    private LocalDateTime dateSend;

    @Column(name = "forward")
    private Long forward;

    @Column(name = "receiver")
    private String receiver;

    @Column(name = "photo_id")
    private String photoId;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    ActionStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_users")
    private User user;
}
