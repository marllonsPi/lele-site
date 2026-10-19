package br.com.crispimcampos.gestao.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {
}
