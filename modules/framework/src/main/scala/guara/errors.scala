package guara

import zio.*

object errors {

  import zio.http.Response
  import zio.http.Status

  case class ReturnResponseError              (response: Response)                   extends Exception
  case class ReturnResponseWithExceptionError (cause: Throwable, response: Response) extends Exception(cause)

  object GuaraError {

    def of(response: Response)                   = ReturnResponseError(response)
    def of(response: Response)(cause: Throwable) = ReturnResponseWithExceptionError(cause, response)

    def fail[A](                  response: Response) : Task[A] = ZIO.fail(of(response))
    def fail[A](cause: Throwable, response: Response) : Task[A] = ZIO.fail(of(response)(cause))
  }
}
