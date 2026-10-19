package br.com.crispimcampos.gestao.agenda;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

	List<Agendamento> findByTitularIdInOrderByPrazoInternoAscIdAsc(Collection<Long> titulares);

	/** Estagiario ve apenas o que lhe foi atribuido. */
	List<Agendamento> findByResponsavelIdOrderByPrazoInternoAscIdAsc(Long responsavelId);
}
