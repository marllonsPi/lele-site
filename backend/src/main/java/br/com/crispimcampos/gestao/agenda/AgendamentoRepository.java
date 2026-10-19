package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

	List<Agendamento> findByTitularIdInOrderByPrazoInternoAscIdAsc(Collection<Long> titulares);

	/** Estagiario ve apenas o que lhe foi atribuido. */
	List<Agendamento> findByResponsavelIdOrderByPrazoInternoAscIdAsc(Long responsavelId);

	/** Agenda de uma pessoa, apenas itens em aberto, restritos aos titulares visiveis. */
	@Query("select a from Agendamento a where a.responsavelId = :resp and a.titularId in :titulares "
			+ "and a.status in :abertos")
	List<Agendamento> findAgenda(@Param("resp") Long responsavelId, @Param("titulares") Collection<Long> titulares,
			@Param("abertos") Collection<StatusAgendamento> abertos);

	/** Agenda do estagiario: so o que lhe foi atribuido. */
	@Query("select a from Agendamento a where a.responsavelId = :resp and a.status in :abertos")
	List<Agendamento> findAgendaAtribuida(@Param("resp") Long responsavelId,
			@Param("abertos") Collection<StatusAgendamento> abertos);

	/** Compromissos que comecam na janela (usado na deteccao de conflitos, sem filtro de visibilidade). */
	@Query("select a from Agendamento a where a.tipo in :tipos and a.inicioEm >= :de and a.inicioEm < :ate")
	List<Agendamento> findCompromissosIniciandoEntre(@Param("tipos") Collection<TipoAgendamento> tipos,
			@Param("de") Instant de, @Param("ate") Instant ate);

	/** Itens de titulares visiveis em certos estados (conferencia, painel). */
	List<Agendamento> findByTitularIdInAndStatusIn(Collection<Long> titulares, Collection<StatusAgendamento> status);

	/** Itens de estagiarios (supervisor): estagiarios sao identificados pelo responsavel. */
	List<Agendamento> findByResponsavelIdInAndStatus(Collection<Long> responsaveis, StatusAgendamento status);
}
