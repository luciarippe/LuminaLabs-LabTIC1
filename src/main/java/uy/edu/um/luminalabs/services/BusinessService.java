package uy.edu.um.luminalabs.services;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.BusinessStatus;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent;
import uy.edu.um.luminalabs.events.BusinessStatusChangedEvent.Change;
import uy.edu.um.luminalabs.exceptions.BusinessNotFoundException;
import uy.edu.um.luminalabs.repositories.BusinessRepository;

import java.util.List;
import java.util.function.Consumer;

// Consultas de emprendimientos y cambios de estado hechos por el administrador (CU-06, CU-11)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BusinessStatusCounts countByStatus() {
        return new BusinessStatusCounts(
                businessRepository.countByStatus(BusinessStatus.PENDING),
                businessRepository.countByStatus(BusinessStatus.APPROVED),
                businessRepository.countByStatus(BusinessStatus.DENIED),
                businessRepository.countByStatus(BusinessStatus.SUSPENDED));
    }

    // status null = todos
    public List<Business> findByStatus(BusinessStatus status) {
        return status == null
                ? businessRepository.findAllByOrderByCreatedAtDesc()
                : businessRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public List<Business> findByProvider(Long providerId) {
        return businessRepository.findDistinctByProviders_IdOrderByCreatedAtDesc(providerId);
    }

    public Business findDetail(Long id) {
        return businessRepository.findWithDetailsById(id).orElseThrow(() -> new BusinessNotFoundException(id));
    }

    @Transactional
    public Business approve(Long id) {
        return changeStatus(id, Business::approve, Change.APPROVED);
    }

    @Transactional
    public Business deny(Long id, String reason) {
        return changeStatus(id, business -> business.deny(reason), Change.DENIED);
    }

    // RF-06: cuando existan reservas, aca tambien hay que cancelarlas con devolucion total
    @Transactional
    public Business suspend(Long id, String reason) {
        return changeStatus(id, business -> business.suspend(reason), Change.SUSPENDED);
    }

    @Transactional
    public Business reactivate(Long id) {
        return changeStatus(id, Business::reactivate, Change.REACTIVATED);
    }

    // Aplica la transicion y avisa a los prestadores por correo (RF-26)
    private Business changeStatus(Long id, Consumer<Business> transition, Change change) {
        Business business = businessRepository.findById(id).orElseThrow(() -> new BusinessNotFoundException(id));
        transition.accept(business);
        eventPublisher.publishEvent(BusinessStatusChangedEvent.of(business, change));
        return business;
    }
}
