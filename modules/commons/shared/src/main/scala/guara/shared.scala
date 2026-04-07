package guara

object shared {
  import java.nio.charset.Charset

  val utf8      = Charset.forName("utf8")
  val code      = "[a-zA-Z0-9_]+".r
  val name      = "[\\w.\\- ]+".r
  val latinName = "[À-ſ\\w.\\-&, ()'/]+".r
}

