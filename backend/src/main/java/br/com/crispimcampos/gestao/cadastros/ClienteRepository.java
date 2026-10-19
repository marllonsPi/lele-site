package br.com.crispimcampos.gestao.cadastros;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByDocumento(String documento);

	/** Clientes que tem ao menos um processo em nome de um dos titulares visiveis. */
	@Query("select distinct p.cliente from Processo p where p.advogadoId in :titulares and p.ativo = true")
	List<Cliente> findComProcessoDe(@Param("titulares") Collection<Long> titulares);
}
