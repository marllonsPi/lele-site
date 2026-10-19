package br.com.crispimcampos.gestao.agenda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ObservacaoRepository extends JpaRepository<ObservacaoAgendamento, Long> {

	List<ObservacaoAgendamento> findByAgendamentoIdOrderByIdAsc(Long agendamentoId);
}
