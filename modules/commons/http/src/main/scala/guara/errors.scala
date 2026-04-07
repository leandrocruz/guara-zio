package guara.http.errors

import zio.*
import zio.http.{Response, Status}

case class ReturnResponseError                    (response: Response)                           extends Exception
case class ReturnResponseWithExceptionError       (cause: Throwable, response: Response)         extends Exception(cause)
case class ReturnErrorCode                        (code: Int)                                    extends Exception
case class ReturnErrorCodeWithMessage             (code: Int, message: String)                   extends Exception(message)
case class ReturnErrorCodeWithException           (code: Int, cause: Throwable)                  extends Exception(cause)
case class ReturnErrorCodeWithMessageAndException (code: Int, message: String, cause: Throwable) extends Exception(message, cause)

object GuaraError {

  def of(response: Response)                   = ReturnResponseError(response)
  def of(response: Response)(cause: Throwable) = ReturnResponseWithExceptionError(cause, response)

  def fail[A](                  response: Response) : Task[A] = ZIO.fail(of(response))
  def fail[A](cause: Throwable, response: Response) : Task[A] = ZIO.fail(of(response)(cause))
}

extension [T](task: Task[T]) {
  def errorToResponse(response: Response) = task.mapError(ReturnResponseWithExceptionError(_, response))
}