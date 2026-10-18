-- V10 gave tickets created before the AI existed the default PENDING, but no AI job was ever
-- queued for them, so they would wait forever. Hand those to the agents, as when AI is off.
update tickets t
   set ai_status = 'SKIPPED'
 where t.ai_status = 'PENDING'
   and not exists (
         select 1
           from scheduled_tasks s
          where s.task_name = 'process-ticket'
            and s.task_instance like 'ticket-' || t.id || '-msg-%');
