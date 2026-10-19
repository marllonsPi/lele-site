package br.com.crispimcampos.gestao.cadastros;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessoRepository extends JpaRepository<Processo, Long> {

	boolean existsByNumero(String numero);

	List<Processo> findByAdvogadoIdInOrderByIdDesc(Collection<Long> titulares);

	List<Processo> findByClienteIdAndAdvogadoIdInOrderByIdDesc(Long clienteId, Collection<Long> titulares);
}
