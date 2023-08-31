package org.pikalovanna.congratulationsbot.service;

import lombok.RequiredArgsConstructor;
import org.pikalovanna.congratulationsbot.CongratulationsBot;
import org.pikalovanna.congratulationsbot.entity.Congratulation;
import org.pikalovanna.congratulationsbot.repository.CongratulationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleSendMessagesService {
    private final CongratulationRepository congratulationRepository;
    private final CongratulationsBot congratulationsBot;

    /**
     * Проверяет наступило ли время отправки поздравлений
     */
    @Scheduled(fixedDelay = 5000L)
    private void scheduleSendMessage() {
        LocalDateTime now = LocalDateTime.now();
        List<Congratulation> congratulations = congratulationRepository.findByDateSendBeforeAndForwardIsNotNull(now);

        for (Congratulation congratulation : congratulations) {
            if (congratulation.getText() != null) {
                String receiver = congratulation.getReceiver() != null ? congratulation.getReceiver() + "\n" : "";
                congratulationsBot.sendSimpleMessage(congratulation.getForward(), receiver + congratulation.getText());
            }
            if (congratulation.getStickerId() != null) {
                congratulationsBot.sendStickerMessage(congratulation.getForward(), congratulation.getStickerId(), null);
            }
            if (congratulation.getPhotoId() != null) {
                congratulationsBot.sendPhotoMessage(congratulation.getForward(), congratulation.getPhotoId(), null);
            }
            congratulation.setDateSend(null);
            congratulationRepository.save(congratulation);
        }
    }
}
