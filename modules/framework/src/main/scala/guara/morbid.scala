package guara.framework.morbid

import zio.*

import guara.framework.config.GuaraConfig
import guara.http.call
import zio.http.*
import zio.json.*

import java.time.LocalDateTime

trait Morbid {
  def start: UIO[Long]
}

case class Password(
  id       : Long,
  user     : Long,
  created  : LocalDateTime,
  deleted  : Option[LocalDateTime],
  method   : String,
  password : String,
  token    : String
)

case class User(
  id       : Long,
  account  : Account,
  created  : LocalDateTime,
  deleted  : Option[LocalDateTime],
  active   : Boolean,
  name     : String,
  email    : String,
  `type`   : String,
  password : Option[Password]
)

case class Account(
  id      : Long,
  active  : Boolean,
  created : LocalDateTime,
  deleted : Option[LocalDateTime],
  name    : String,
  `type`  : String
)

case class UserCache(users: Seq[User], etag: Option[String] = None)

given JsonDecoder[Password] = DeriveJsonDecoder.gen[Password]
given JsonDecoder[Account]  = DeriveJsonDecoder.gen[Account]
given JsonDecoder[User]     = DeriveJsonDecoder.gen[User]

case class RemoteMorbid(config: GuaraConfig, ref: Ref[UserCache], client: Client, scope: Scope) extends Morbid {

  private given Client = client
  private given Scope  = scope

  private val magic = Headers("X-Morbid-Magic" -> config.morbid.magic)

  private def update = {

    def parseAndUpdateCache(response: Response) = {
      //response.headers.foreach(println)
      val next = response.headers.get(Header.ETag.name)
      for {
        body     <- response.body.asString
        users    <- ZIO.fromEither(body.fromJson[Seq[User]]).mapError(new Exception(_))
        _        <- ref.update(_.copy(users = users, etag = next))
        _        <- ZIO.logInfo(s"User Cache Updated (etag: ${next.getOrElse("_")})")
      } yield users
    }

    def headers(cache: UserCache): Headers = {
      magic ++ cache
        .etag
        .map(value => Headers(Header.IfNoneMatch.name -> value))
        .getOrElse(Headers.empty)
    }

    for {
      cache    <- ref.get
      prev     =  cache.etag
      _        <- ZIO.logInfo(s"Updating User Cache (etag: ${prev.getOrElse("_")})")
      response <- call(ZClient.request(Request.get(config.morbid.url).addHeaders(headers(cache))))
      users    <- ZIO.whenCase(response.status.code) {
        case 200  => parseAndUpdateCache(response)
        case 304  => ZIO.logInfo("User Cache Not Modified") *> ZIO.succeed(cache.users)
        case code => ZIO.fail(new Exception(s"Error Updating User Cache (status: ${code})"))
      }
    } yield users
  }

  override def start = {
    update
      .catchAll(e => ZIO.logError(s"Error Retrieving Users: ${e.getMessage}"))
      .repeat(Schedule.fixed(config.morbid.updateEvery))
  }
}

object Morbid {
  val layer = ZLayer.fromFunction(RemoteMorbid.apply)
}