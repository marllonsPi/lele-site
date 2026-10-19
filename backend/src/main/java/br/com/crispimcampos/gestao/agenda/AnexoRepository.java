package br.com.crispimcampos.gestao.agenda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnexoRepository extends JpaRepository<Anexo, Long> {

	List<Anexo> findByProcessoIdOrderByIdAsc(Long processoId);

	List<Anexo> findByAgendamentoIdOrderByIdAsc(Long agendamentoId);
}
