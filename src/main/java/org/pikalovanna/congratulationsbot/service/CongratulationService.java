package org.pikalovanna.congratulationsbot.service;

import org.pikalovanna.congratulationsbot.entity.Congratulation;
import org.pikalovanna.congratulationsbot.repository.CongratulationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CongratulationService {

    @Autowired
    private final CongratulationRepository congratulationRepository;

    public CongratulationService(CongratulationRepository congratulationRepository) {
        this.congratulationRepository = congratulationRepository;
    }

    public Page<Congratulation> getPages(String username, Pageable pageable){
        return congratulationRepository.findByUserUsernameOrderByIdAsc(username, pageable);
    }

    public Congratulation getLast(String username){
        List<Congratulation> congratulations = congratulationRepository.findByUserUsernameOrderByIdAsc(username);
        if (!congratulations.isEmpty())
            return congratulations.get(congratulations.size() - 1);
        else
            return null;
    }

    public List<Congratulation> getAll(String username){
        return congratulationRepository.findByUserUsernameOrderByIdAsc(username);
    }

    public Congratulation saveCongratulation(Congratulation congratulation) {
        return congratulationRepository.save(congratulation);
    }

    public void deleteAllByUserId(Long userId){
        List<Congratulation> congratulations = congratulationRepository.findByUserId(userId);
        for (Congratulation congratulation : congratulations) {
            congratulationRepository.delete(congratulation);
        }
    }

    public void deleteCongratulation(Long congratulation_id) {
        congratulationRepository.deleteById(congratulation_id);
    }

    public boolean hasText(Congratulation congratulation){
        return congratulation.getText() != null;
    }
    public boolean hasSticker(Congratulation congratulation){
        return congratulation.getStickerId() != null;
    }
    public boolean hasPhoto(Congratulation congratulation){
        return congratulation.getPhotoId() != null;
    }
}
