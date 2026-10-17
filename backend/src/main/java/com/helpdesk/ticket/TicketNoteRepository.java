package com.helpdesk.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketNoteRepository extends JpaRepository<TicketNote, Long> {

	List<TicketNote> findByTicketIdOrderByIdAsc(Long ticketId);

}
