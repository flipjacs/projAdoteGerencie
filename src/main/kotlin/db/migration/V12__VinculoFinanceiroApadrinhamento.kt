package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/** V9 used unnamed CHECKs. Discover only its category/origin checks, whose
 * generated names differ between MySQL and H2; leave all other checks intact. */
class V12__VinculoFinanceiroApadrinhamento:BaseJavaMigration() {
 override fun migrate(context:Context) {
  val c=context.connection
  val mysql=c.metaData.databaseProductName.equals("MySQL",ignoreCase=true)
  val schema=if(mysql)c.catalog else c.schema
  val checks=mutableListOf<String>()
  c.prepareStatement("""SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
   JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
   WHERE tc.TABLE_SCHEMA=? AND LOWER(tc.TABLE_NAME)='movimentacao_financeira' AND tc.CONSTRAINT_TYPE='CHECK'""").use { q ->
   q.setString(1,schema);q.executeQuery().use { r ->while(r.next())if(r.getString(2).contains("categoria",ignoreCase=true))checks+=r.getString(1)}
  }
  check(checks.size==2){"Esperadas duas restrições de categoria/origem do financeiro V9"}
  c.createStatement().use { s ->
   s.execute("ALTER TABLE movimentacao_financeira ADD COLUMN parcela_apadrinhamento_id BIGINT NULL")
   s.execute("ALTER TABLE movimentacao_financeira ADD CONSTRAINT uq_movimento_parcela UNIQUE(parcela_apadrinhamento_id)")
   s.execute("ALTER TABLE movimentacao_financeira ADD CONSTRAINT fk_movimento_parcela FOREIGN KEY(ong_id,parcela_apadrinhamento_id) REFERENCES parcela_apadrinhamento(ong_id,id)")
   checks.forEach { name ->require(name.matches(Regex("[A-Za-z0-9_]+")));s.execute("ALTER TABLE movimentacao_financeira DROP ${if(mysql)"CHECK"else"CONSTRAINT"} ${if(mysql) "`$name`" else "\"$name\""}") }
   s.execute("""ALTER TABLE movimentacao_financeira ADD CONSTRAINT ck_movimento_categoria_k81 CHECK (
    (tipo='RECEITA' AND categoria IN ('DOACAO','APADRINHAMENTO','EVENTO','OUTRA_RECEITA') AND status<>'APROVADA') OR
    (tipo='DESPESA' AND categoria IN ('ALIMENTACAO','MEDICAMENTOS','VETERINARIO','TRANSPORTE','MANUTENCAO','OUTRA_DESPESA')))""")
   s.execute("""ALTER TABLE movimentacao_financeira ADD CONSTRAINT ck_movimento_origem_k81 CHECK (
    (doacao_id IS NULL AND parcela_apadrinhamento_id IS NULL AND categoria NOT IN ('DOACAO','APADRINHAMENTO')) OR
    (doacao_id IS NOT NULL AND parcela_apadrinhamento_id IS NULL AND tipo='RECEITA' AND categoria='DOACAO' AND status='REALIZADA') OR
    (doacao_id IS NULL AND parcela_apadrinhamento_id IS NOT NULL AND tipo='RECEITA' AND categoria='APADRINHAMENTO' AND status='REALIZADA'))""")
  }
 }
}
