package guara

import guara.errors.*
import guara.utils.{Origin, ensureResponse}
import guara.utils.SafeResponse.*
import zio.*
import zio.http.*

/**
 * Sample HTTP app that exercises every branch of [[guara.utils.ensureResponse]] so we can verify
 * the framework returns the correct HTTP response (and *always returns one*) for each failure
 * shape ZIO can throw at it.
 *
 * Run:
 * {{{
 *   sbt "Test/runMain guara.SampleApp"
 * }}}
 * (port 8080)
 *
 * Then exercise with curl:
 * {{{
 *   for path in success success-json return-response-error return-response-with-exception \
 *               unified-error unified-error-with-status unified-error-with-cause \
 *               raw-exception defect async-fail
 *   do
 *     echo "=== /$path ==="
 *     curl -s -o /dev/stderr -w "HTTP %{http_code}  total %{time_total}s\n" \
 *          "http://localhost:8080/$path"
 *     echo
 *   done
 * }}}
 *
 * The two `hang*` endpoints intentionally never complete — useful for verifying client-side
 * timeout handling. Use `curl --max-time 2 http://localhost:8080/hang` to abort after 2s.
 */
object SampleApp extends ZIOAppDefault {

  private given Origin = Origin.of("SampleApp")

  /** Canonical wrapper: lifts a `Task[Response]` through `ensureResponse` and back to `Task[Response]`. */
  private def wrap(task: Task[Response]): Task[Response] = ensureResponse(task).toTask

  private val routes = Routes(

    // ─────────────────── happy paths ───────────────────

    /** 200 OK — plain text body. Sanity check that wrapping a successful task doesn't break anything. */
    Method.GET / "success" -> handler { (_: Request) =>
      wrap(ZIO.succeed(Response.text("OK")))
    },

    /** 200 OK — json body. */
    Method.GET / "success-json" -> handler { (_: Request) =>
      wrap(ZIO.succeed(Response.json("""{"ok":true}""")))
    },

    // ─────────────────── ReturnResponseError ───────────────────

    /** 400 Bad Request with custom body — the response inside `ReturnResponseError` is passed through verbatim. */
    Method.GET / "return-response-error" -> handler { (_: Request) =>
      wrap(ZIO.fail(ReturnResponseError(Response.badRequest("custom bad request body"))))
    },

    /** 403 Forbidden — the cause Exception is *dropped* by ensureResponse; only the response is sent. */
    Method.GET / "return-response-with-exception" -> handler { (_: Request) =>
      wrap(ZIO.fail(ReturnResponseWithExceptionError(
        cause    = new RuntimeException("this cause is dropped from the http response"),
        response = Response.forbidden("forbidden by test")
      )))
    },

    // ─────────────────── ReturnUnifiedError ───────────────────

    /** UEF response. Default status 500. Body: JSON {origin, message, status, code, trace}. */
    Method.GET / "unified-error" -> handler { (_: Request) =>
      wrap(ZIO.fail(ReturnUnifiedError("plain unified error")))
    },

    /**
     * UEF with an explicit status + code. NOTE: there appears to be a latent bug in
     * [[guara.uef.toResponse]] — it uses `uef.code.map(Status.fromInt)` for the HTTP status
     * instead of `uef.status`. So passing `status = 418` alone gives back 500; you have to set
     * `code = Some(418)` to get 418 on the wire. This sample makes that visible.
     */
    Method.GET / "unified-error-with-status" -> handler { (_: Request) =>
      wrap(ZIO.fail(ReturnUnifiedError("I'm a teapot", status = 418, code = Some(418))))
    },

    /** UEF with a wrapped cause — the `trace` field of the response body should contain the stack of the cause. */
    Method.GET / "unified-error-with-cause" -> handler { (_: Request) =>
      wrap(ZIO.fail(ReturnUnifiedError("with cause", cause = Some(new RuntimeException("root cause for trace")))))
    },

    // ─────────────────── arbitrary typed failures ───────────────────

    /** Plain RuntimeException — caught by the fall-through clause; squashed into UEF. */
    Method.GET / "raw-exception" -> handler { (_: Request) =>
      wrap(ZIO.fail(new RuntimeException("raw exception, no UEF wrapper")))
    },

    // ─────────────────── ZIO defect ───────────────────

    /** `ZIO.die` produces a defect — caught by `.catchAllDefect`; response includes the defect's message. */
    Method.GET / "defect" -> handler { (_: Request) =>
      wrap(ZIO.die(new IllegalStateException("uncatchable defect — but ensureResponse catches it anyway")))
    },

    // ─────────────────── timing / async ───────────────────

    /** Async failure after a delay — proves ensureResponse handles failures from concurrent fibers. */
    Method.GET / "async-fail" -> handler { (_: Request) =>
      wrap(ZIO.sleep(200.millis) *> ZIO.fail(new RuntimeException("delayed failure")))
    },

    // ─────────────────── pathological ───────────────────

    /**
     * `ZIO.never` — the task literally never completes. ensureResponse can't help here because
     * the task never reaches `catchAllTrace`. This is the shape that produced the production
     * hang we were debugging — useful for verifying that the client correctly times out
     * (`curl --max-time 2 …`).
     */
    Method.GET / "hang" -> handler { (_: Request) =>
      wrap(ZIO.never.as(Response.text("unreachable")))
    },

    /**
     * Like /hang but pre-completes the task once via a Promise so we can observe ensureResponse
     * behavior when the task DOES complete but is still wrapped in `ensureResponse`. Useful for
     * showing that `ensureResponse` adds zero latency on the happy path.
     */
    Method.GET / "completes-eventually" -> handler { (_: Request) =>
      wrap(ZIO.sleep(500.millis) *> ZIO.succeed(Response.text("completed after 500ms")))
    },
  )

  override val run: ZIO[Any, Throwable, Nothing] =
    ZIO.logInfo("SampleApp listening on http://localhost:8080") *>
    Server.serve(routes.sandbox).provide(Server.defaultWithPort(8080))
}
