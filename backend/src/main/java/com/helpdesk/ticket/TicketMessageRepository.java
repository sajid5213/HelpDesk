package com.helpdesk.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

	List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);

}
