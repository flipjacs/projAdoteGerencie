package br.com.sysanimais.api.credenciais

import br.com.sysanimais.api.auth.service.LoginRateLimiter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/** Limita requisições inclusive inválidas antes de desserializar o corpo. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
class PrimeiroAcessoProtecaoFilter : OncePerRequestFilter() {
 private val limite=LoginRateLimiter(originLimit=30,pairLimit=30,globalLimit=120,pairWindowMs=60_000)
 private val maxBytes=8192
 override fun shouldNotFilter(request: HttpServletRequest): Boolean =
  request.method!="POST" || request.requestURI.removePrefix(request.contextPath)!="/api/auth/primeiro-acesso/trocar-senha"
 override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
  response.setHeader("Cache-Control","no-store")
  val retry=limite.acquire(request.remoteAddr ?: "unknown","primeiro-acesso")
  if(retry>0) {
   response.status=429;response.setHeader("Retry-After",retry.toString());response.contentType="application/problem+json"
   response.writer.write("""{"status":429,"detail":"Muitas tentativas. Tente novamente mais tarde."}""");return
  }
  if(request.contentLengthLong>maxBytes) { response.status=413;return }
  val body=request.inputStream.readNBytes(maxBytes+1)
  if(body.size>maxBytes) { response.status=413;return }
  val wrapped=object: HttpServletRequestWrapper(request) {
   override fun getContentLength()=body.size
   override fun getContentLengthLong()=body.size.toLong()
   override fun getInputStream(): ServletInputStream {
    val input=ByteArrayInputStream(body)
    return object: ServletInputStream() {
     override fun read()=input.read()
     override fun isFinished()=input.available()==0
     override fun isReady()=true
     override fun setReadListener(listener: ReadListener?) { throw UnsupportedOperationException("Leitura assíncrona indisponível") }
    }
   }
   override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream,Charset.forName(characterEncoding ?: "UTF-8")))
  }
  chain.doFilter(wrapped,response)
 }
}
