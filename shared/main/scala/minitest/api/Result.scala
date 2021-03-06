package minitest.api

import scala.util.control.NonFatal
import scala.compat.Platform.EOL
import scala.Console.{GREEN, RED, YELLOW}

sealed trait Result[+T] {
  def formatted(name: String): String

  def map[U](f: T => U): Result[U] =
    this match {
      case Result.Success(value) =>
        try Result.Success(f(value)) catch {
          case NonFatal(ex) =>
            Result.Exception(ex, None)
        }
      case error =>
        error.asInstanceOf[Result[Nothing]]
    }

  def flatMap[U](f: T => Result[U]): Result[U] =
    this match {
      case Result.Success(value) =>
        try f(value) catch {
          case NonFatal(ex) =>
            Result.Exception(ex, None)
        }
      case error =>
        error.asInstanceOf[Result[Nothing]]
    }
}

object Result {
  case class Success[+T](value: T) extends Result[T] {
    def formatted(name: String): String = {
      GREEN + "- " + name + EOL
    }
  }

  case class Ignored(reason: Option[String], location: Option[SourceLocation]) extends Result[Nothing] {
    def formatted(name: String): String = {
      val reasonWithLocation = reason.map { msg =>
        val string = msg + location.fold("")(l => s" (${l.path}:${l.line})")
        YELLOW + "  " + string + EOL
      }

      YELLOW + "- " + name + " !!! IGNORED !!!" + EOL +
      reasonWithLocation.getOrElse("")
    }
  }

  case class Canceled(reason: Option[String], location: Option[SourceLocation]) extends Result[Nothing] {
    def formatted(name: String): String = {
      val reasonWithLocation = reason.map { msg =>
        val string = msg + location.fold("")(l => s" (${l.path}:${l.line})")
        YELLOW + "  " + string + EOL
      }

      YELLOW + "- " + name + " !!! CANCELED !!!" + EOL +
      reasonWithLocation.getOrElse("")
    }
  }

  case class Failure(msg: String, source: Option[Throwable], location: Option[SourceLocation])
    extends Result[Nothing] {
    
    def formatted(name: String): String = {
      val message = msg + location.fold("")(l => s" (${l.path}:${l.line})")
      RED + s"- $name *** FAILED ***" + EOL +
      RED +  "  " + message + EOL
    }
  }

  case class Exception(source: Throwable, location: Option[SourceLocation])
    extends Result[Nothing] {
    
    def formatted(name: String): String = {
      val description = {
        val name = source.getClass.getName
        val className = name.substring(name.lastIndexOf(".") + 1)
        val msg = Option(source.getMessage).filterNot(_.isEmpty)
          .fold(className)(m => s"$className: $m")
        location.fold(msg)(l => s"$msg (${l.path}:${l.line})")
      }

      val stackTrace = {
        val lst = source.getStackTrace
        val ending = if (lst.length > 20) EOL + RED + "    ..." + EOL else EOL
        lst.take(20).mkString("", EOL + "    " + RED, ending)
      }

      s"$RED- $name *** FAILED ***$EOL$RED  $description$EOL$RED" +
      s"$RED    $stackTrace"
    }
  }

  def from(error: Throwable) = error match {
    case ex: AssertionException =>
      Result.Failure(ex.message, Some(ex), Some(ex.location))
    case ex: UnexpectedException =>
      Result.Exception(ex.reason, Some(ex.location))
    case ex: IgnoredException =>
      Result.Ignored(ex.reason, ex.location)
    case ex: CanceledException =>
      Result.Canceled(ex.reason, ex.location)
    case other =>
      Result.Exception(other, None)
  }
}
