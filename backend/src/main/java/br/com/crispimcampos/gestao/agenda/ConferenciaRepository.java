package br.com.crispimcampos.gestao.agenda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConferenciaRepository extends JpaRepository<Conferencia, Long> {

	List<Conferencia> findByAgendamentoIdOrderByIdAsc(Long agendamentoId);
}
