package br.com.familhas.admin.booking;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.familhas.booking.BookingEntity;

/** Consultas do Admin sobre reservas (filtros dinâmicos). As escritas continuam só no BookingRepository. */
public interface AdminBookingRepository extends JpaRepository<BookingEntity, UUID>,
        JpaSpecificationExecutor<BookingEntity> {
}
