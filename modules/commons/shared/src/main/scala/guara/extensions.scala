package guara.extensions

import zio.*

extension [T](op: Option[T]) {
  def orFail(message: String): Task[T] = ZIO.fromOption(op).mapError(_ => Exception(message))
}

extension [T](task: Task[Option[T]]) {
  def orFail(message: String): Task[T] = {
    for
      maybe <- task
      value <- ZIO.fromOption(maybe).mapError(_ => Exception(message))
    yield value
  }
}

extension [T](task: Task[T]) {
  def refineError(message: String): Task[T] = task.mapError(Exception(message, _))
}



