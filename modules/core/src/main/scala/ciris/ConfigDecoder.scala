package ciris

import ciris.api._
import ciris.api.syntax._
import ciris.ConfigError.wrongType
import ciris.decoders.ConfigDecoders

import scala.util.{Failure, Success, Try}

/**
  * [[ConfigDecoder]] represents the ability to convert the value
  * of a [[ConfigEntry]] to a different type. A [[ConfigDecoder]]
  * supports converting values of type `A` to values of type `B`,
  * within a context `F`, while also supporting sensible error
  * messages.<br>
  *<br>
  * To create a new [[ConfigDecoder]], simply extended the class
  * and implement the [[decode]] method. Alternatively, refer to
  * the companion object for helper methods.<br>
  *<br>
  * Note that most [[ConfigDecoder]] instances provided by Ciris
  * support converting from `String` to some type `B`, which
  * should be enough for most use cases.
  *
  * @tparam A the type from which the decoder converts
  * @tparam B the type to which the decoder converts
  */
abstract class ConfigDecoder[A, B] { self =>

  /**
    * Decodes the value of the specified [[ConfigEntry]], converting
    * the value from type `A` to type `B`, within a context `F`,
    * while also supporting sensible error messages.
    *
    * @param entry the [[ConfigEntry]] for which to decode the value
    * @tparam F the context in which to decode the configuration value
    * @tparam K the type of the key read from the configuration source
    * @tparam S the type of the original configuration source value
    * @return the decoded value or a [[ConfigError]] if decoding failed
    */
  def decode[F[_]: Monad, K, S](
    entry: ConfigEntry[F, K, S, A]
  ): F[Either[ConfigError, B]]

  /**
    * Applies a function to the converted value from this [[ConfigDecoder]].
    * The specified function is only applied if the conversion to `B` was
    * successful, otherwise the behaviour remains unchanged.
    *
    * @param f the function to apply to the value
    * @tparam C the type for which to convert the value to
    * @return a new `ConfigDecoder[A, C]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].map(_.take(2))
    * decoder: ConfigDecoder[String, String] = ConfigDecoder$$$$anon$$1@2274f2dd
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, String] = Right(12)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, String] = Right(ab)
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, String] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def map[C](f: B => C): ConfigDecoder[A, C] =
    new ConfigDecoder[A, C] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, C]] = {
        self.decode(entry).map(_.fold(Left.apply, value => Right(f(value))))
      }
    }

  /**
    * Applies a function on the converted value, returning an `Option[C]`.
    * If the function returns `None`, the type conversion to `C` will be
    * considered to have failed. Returning a `Some` will be interpreted
    * like the conversion succeeded.
    *
    * @param typeName the name of the type `C`
    * @param f the function converting from `B` to `Option[C]`
    * @tparam C the type for which to convert the value to
    * @return a new `ConfigDecoder[A, C]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].mapOption("Int")(value => scala.util.Try(value.toInt).toOption)
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$2@669d8d59
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(abc), abc, Int))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, Int] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def mapOption[C](typeName: String)(f: B => Option[C]): ConfigDecoder[A, C] =
    new ConfigDecoder[A, C] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, C]] = {
        for {
          sourceValue <- entry.sourceValue
          decoded <- self.decode(entry)
        } yield {
          decoded.fold(Left.apply, value => {
            f(value) match {
              case Some(a) => Right(a)
              case None =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, None))
            }
          })
        }
      }
    }

  /**
    * Applies a function on the converted value, returning a `Try[C]`.
    * If the function returns a `Success`, the type conversion will
    * be considered successful. Returning a `Failure` means that
    * the conversion failed.
    *
    * @param typeName the name of the type `C`
    * @param f the function converting from `B` to `Try[C]`
    * @tparam C the type for which to convert the value to
    * @return a new `ConfigDecoder[A, C]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].mapTry("Int")(value => scala.util.Try(value.toInt))
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$3@380729e4
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(abc), abc, Int, java.lang.NumberFormatException: For input string: "abc"))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, Int] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def mapTry[C](typeName: String)(f: B => Try[C]): ConfigDecoder[A, C] =
    new ConfigDecoder[A, C] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, C]] = {
        for {
          sourceValue <- entry.sourceValue
          decoded <- self.decode(entry)
        } yield {
          decoded.fold(
            Left.apply,
            value => {
              f(value) match {
                case Success(a) => Right(a)
                case Failure(cause) =>
                  Left(
                    wrongType(
                      entry.key,
                      entry.keyType,
                      sourceValue,
                      value,
                      typeName,
                      Some(cause)
                    ))
              }
            }
          )
        }
      }
    }

  /**
    * Applies a function on the converted value to `C`, making sure to catch
    * any non-fatal exceptions thrown by the function. The conversion will
    * be considered successful only if the function does not throw an
    * exception.
    *
    * @param typeName the name of the type `C`
    * @param f the function converting from `B` to `C`
    * @tparam C the type for which to convert the value to
    * @return a new `ConfigDecoder[A, C]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].mapCatchNonFatal("Int")(_.toInt)
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder@17323c05
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(abc), abc, Int, java.lang.NumberFormatException: For input string: "abc"))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, Int] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def mapCatchNonFatal[C](typeName: String)(f: B => C): ConfigDecoder[A, C] =
    mapTry(typeName)(value => Try(f(value)))

  /**
    * Applies a partial function on the converted value. The type conversion to
    * `C` will only succeed for values which the partial function is defined.
    *
    * @param typeName the name of the type `C`
    * @param f the partial function converting from `B` to `C`
    * @tparam C the type for which to convert the value to
    * @return a new `ConfigDecoder[A, C]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "-123"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].collect("PosBigInt") { case s if s.forall(_.isDigit) => BigInt(s) }
    * decoder: ConfigDecoder[String, scala.math.BigInt] = ConfigDecoder$$$$anon$$2@727cfc59
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, scala.math.BigInt] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, scala.math.BigInt] = Left(WrongType(1, Argument, Right(-123), -123, PosBigInt))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, scala.math.BigInt] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def collect[C](typeName: String)(f: PartialFunction[B, C]): ConfigDecoder[A, C] =
    mapOption(typeName) {
      case value if f.isDefinedAt(value) => Some(f(value))
      case _                             => None
    }

  /**
    * Applies a function on the converted value, returning an `Either[L, R]`.
    * If the function returns `Left[L, R]`, the type conversion to `R` will
    * be considered to have failed. Returning a `Right[L, R]` means that
    * the conversion succeeded.
    *
    * @param typeName the name of the type `R`
    * @param f the function converting from `B` to `Either[L, R]`
    * @tparam L the type representing an error for the type conversion;<br>
    *           should have a sensible `toString` method for error messages
    * @tparam R the type for which to convert the value to
    * @return a new `ConfigDecoder[A, R]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String].mapEither("Int")(value => scala.util.Try(value.toInt).map(Right.apply).recover { case e => Left(e) }.get)
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$3@8635c89
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError,Int] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError,Int] = Left(WrongType(1, Argument, Right(abc), abc, Int, java.lang.NumberFormatException: For input string: "abc"))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError,Int] = Left(MissingKey(2, Argument))
    * }}}
    */
  final def mapEither[L, R](typeName: String)(f: B => Either[L, R]): ConfigDecoder[A, R] =
    new ConfigDecoder[A, R] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, R]] = {
        for {
          sourceValue <- entry.sourceValue
          decoded <- self.decode(entry)
        } yield {
          decoded.fold(
            Left.apply,
            value => {
              f(value) match {
                case Right(r) => Right(r)
                case Left(cause) =>
                  Left(
                    wrongType(
                      entry.key,
                      entry.keyType,
                      sourceValue,
                      value,
                      typeName,
                      Some(cause)
                    ))
              }
            }
          )
        }
      }
    }

  /**
    * Applies a function on the values in the [[ConfigEntry]]s decoded by
    * this decoder, before trying to convert the value to type `B`. This
    * method returns a new [[ConfigDecoder]] with the behavior, leaving
    * the existing [[ConfigDecoder]] unmodified.
    *
    * @param f the function to apply on the [[ConfigEntry]] value
    * @return a new [[ConfigDecoder]] decoding modified entry values
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123 "))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String, Int].mapEntryValue(_.trim)
    * decoder: ConfigDecoder[Int] = ciris.ConfigDecoder$$$$anon$$5@57c04ac9
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError,Int] = Right(123)
    * }}}
    */
  final def mapEntryValue(f: A => A): ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        self.decode(entry.mapValue(f))
      }
    }

  /**
    * Removes any potentially sensitive details, like configuration
    * values, from any errors returned as a result of decoding with
    * this [[ConfigDecoder]].
    *
    * @return a new [[ConfigDecoder]]
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123 "))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder[String, Int]
    * decoder: ConfigDecoder[Int] = ciris.ConfigDecoder$$$$anon$$6@7bdb183f
    *
    * scala> decoder.decode(source.read(0)).left.map(_.message).toString
    * res0: String = Left(Command-line argument [0] with value [123 ] cannot be converted to type [Int]: java.lang.NumberFormatException: For input string: "123 ")
    *
    * scala> val decoderRedacted = decoder.redactSensitive
    * decoderRedacted: ConfigDecoder[Int] = ciris.ConfigDecoder$$$$anon$$6@28aa7479
    *
    * scala> decoderRedacted.decode(source.read(0)).left.map(_.message).toString
    * res1: String = Left(Command-line argument [0] with value [<redacted>] cannot be converted to type [Int])
    * }}}
    */
  final def redactSensitive: ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        self.decode(entry).map {
          case Left(error)      => Left(error.redactSensitive)
          case right @ Right(_) => right
        }
      }
    }

  override def toString: String =
    "ConfigDecoder$" + System.identityHashCode(this)
}

object ConfigDecoder extends ConfigDecoders {

  /**
    * Attempt to implicitly find a [[ConfigDecoder]] which converts
    * values from type `A` to type `B`, and when found, return the
    * [[ConfigDecoder]] instance.
    *
    * @param decoder the implicit [[ConfigDecoder]] instance
    * @tparam A the type from which to convert the value
    * @tparam B the type to which to convert the value
    * @return the found [[ConfigDecoder]] instance
    */
  def apply[A, B](implicit decoder: ConfigDecoder[A, B]): ConfigDecoder[A, B] =
    decoder

  /**
    * A [[ConfigDecoder]] which does not modify the value read from a
    * configuration source. This method is an alias and equivalent of
    * [[ConfigDecoder#identity]].
    *
    * @tparam A the type from which to convert the value
    * @return a new [[ConfigDecoder]] which does not modify values
    */
  def apply[A]: ConfigDecoder[A, A] =
    identity[A]

  /**
    * A [[ConfigDecoder]] which does not modify the value read from a
    * configuration source. Most often not useful on its own, but can
    * be used as a starting point for other types of [[ConfigDecoder]]s.
    *
    * @tparam A the type from which to convert the value
    * @return a new [[ConfigDecoder]] which does not modify values
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.identity[String]
    * decoder: ConfigDecoder[String, String] = ConfigDecoder$$$$anon$$4@245c7250
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, String] = Right(123456)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, String] = Right(abc)
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, String] = Left(MissingKey(2, Argument))
    * }}}
    */
  def identity[A]: ConfigDecoder[A, A] =
    new ConfigDecoder[A, A] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, A]] =
        entry.value
    }

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in both
    * the error case and in the value case.
    *
    * @param onError the function to apply in the case of an error
    * @param onValue the function to apply in case of a value
    * @tparam A the type from which to convert the value
    * @tparam B the type to which to convert the value
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456", "abc"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.fold(error => Left(error), (value: String) => Right(value + "/789"))
    * decoder: ConfigDecoder[String, String] = ConfigDecoder$$$$anon$$4@76e4848f
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, String] = Right(123456/789)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, String] = Right(abc/789)
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, String] = Left(MissingKey(2, Argument))
    * }}}
    */
  def fold[A, B](
    onError: ConfigError => Either[ConfigError, B],
    onValue: A => Either[ConfigError, B]
  ): ConfigDecoder[A, B] = {
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] =
        entry.value.map(_.fold(onError, onValue))
    }
  }

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in the case
    * when a value was successfully read from the configuration source.
    *
    * @param f the function to apply in case of a value
    * @tparam A the type from which to convert the value
    * @tparam B the type to which to convert the value
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("123456"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.flatMap { value: String => Right(value.take(2)) }
    * decoder: ConfigDecoder[String, String] = ConfigDecoder$$$$anon$$4@77f11239
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, String] = Right(12)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, String] = Left(MissingKey(1, Argument))
    * }}}
    */
  def flatMap[A, B](f: A => Either[ConfigError, B]): ConfigDecoder[A, B] =
    ConfigDecoder.fold[A, B](Left.apply, f)

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in the case
    * when a value was successfully read from the configuration source,
    * returning an `Option[B]`. If the function returns `None`, it will
    * be interpreted as if the conversion to type `B` failed.
    *
    * @param typeName the name of the type `B`
    * @param f the function to apply on the value, returning `Option[B]`
    * @tparam A the type from which to convert the value
    * @tparam B the type to which to convert the value
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("1", "25"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.fromOption("String") { s: String => Some(s).filter(_.length == 1) }
    * decoder: ConfigDecoder[String, String] = ConfigDecoder$$$$anon$$5@4826b7ae
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, String] = Right(1)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, String] = Left(WrongType(1, Argument, Right(25), 25, String))
    * }}}
    */
  def fromOption[A, B](typeName: String)(f: A => Option[B]): ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        for {
          sourceValue <- entry.sourceValue
          errorOrValue <- entry.value
        } yield {
          errorOrValue.flatMap { value =>
            f(value) match {
              case Some(t) => Right(t)
              case None =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, None))
            }
          }
        }
      }
    }

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in the case
    * when a value was successfully read from the configuration source,
    * returning a `Try[B]`. If the function returns `Failure`, it will
    * be interpreted as if the conversion to type `B` failed.
    *
    * @param typeName the name of the type `B`
    * @param f the function to apply on the value, returning `Try[B]`
    * @tparam B the type of value in the specified function
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("1", "a"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.fromTry("Int") { value: String => scala.util.Try(value.toInt) }
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$6@26db094b
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(1)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(a), a, Int, java.lang.NumberFormatException: For input string: "a"))
    * }}}
    */
  def fromTry[A, B](typeName: String)(f: A => Try[B]): ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        for {
          sourceValue <- entry.sourceValue
          errorOrValue <- entry.value
        } yield {
          errorOrValue.flatMap { value =>
            f(value) match {
              case Success(a) => Right(a)
              case Failure(cause) =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, Some(cause)))
            }
          }
        }
      }
    }

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in the case
    * when a value was successfully read from the configuration source,
    * returning a `Try[Option[B]]`. The conversion will only succeed
    * if the function returns `Success[Some[B]]`.
    *
    * @param typeName the name of the type `B`
    * @param f the function to apply on the value, returning `Try[Option[B]]`
    * @tparam B the type to convert to
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("1", "1234", "a"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.fromTryOption("Int") { value: String => scala.util.Try(if(value.length < 4) Some(value.toInt) else None) }
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$9@7d20803b
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(1)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(1234), 1234, Int))
    *
    * scala> decoder.decode(source.read(2))
    * res2: Either[ConfigError, Int] = Left(WrongType(2, Argument, Right(a), a, Int, java.lang.NumberFormatException: For input string: "a"))
    *
    * scala> decoder.decode(source.read(3))
    * res3: Either[ConfigError, Int] = Left(MissingKey(3, Argument))
    * }}}
    */
  def fromTryOption[A, B](typeName: String)(f: A => Try[Option[B]]): ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        for {
          sourceValue <- entry.sourceValue
          errorOrValue <- entry.value
        } yield {
          errorOrValue.flatMap { value =>
            f(value) match {
              case Success(Some(value)) =>
                Right(value)
              case Success(None) =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, None))
              case Failure(cause) =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, Some(cause)))
            }
          }
        }
      }
    }

  /**
    * Creates a new [[ConfigDecoder]] by applying a function in the case
    * when a value was successfully read from the configuration source,
    * wrapping the function in a `Try`. If the function, for any
    * reason, throws an exception, it will be interpreted as if
    * the conversion to type `B` failed.
    *
    * @param typeName the name of the type `B`
    * @param f the function to apply on the value, returning `B`
    * @tparam B the type of value in the specified function
    * @return a new `ConfigDecoder[A, B]`
    * @example {{{
    * scala> val source = ConfigSource.byIndex(ConfigKeyType.Argument)(Vector("1", "a"))
    * source: ConfigSource[Int, String] = ConfigSource(Argument)
    *
    * scala> val decoder = ConfigDecoder.catchNonFatal("Int") { s: String => s.toInt }
    * decoder: ConfigDecoder[String, Int] = ConfigDecoder$$$$anon$$7@4fee5a39
    *
    * scala> decoder.decode(source.read(0))
    * res0: Either[ConfigError, Int] = Right(1)
    *
    * scala> decoder.decode(source.read(1))
    * res1: Either[ConfigError, Int] = Left(WrongType(1, Argument, Right(a), a, Int, java.lang.NumberFormatException: For input string: "a"))
    * }}}
    */
  def catchNonFatal[A, B](typeName: String)(f: A => B): ConfigDecoder[A, B] =
    new ConfigDecoder[A, B] {
      override def decode[F[_]: Monad, K, S](
        entry: ConfigEntry[F, K, S, A]
      ): F[Either[ConfigError, B]] = {
        for {
          sourceValue <- entry.sourceValue
          errorOrValue <- entry.value
        } yield {
          errorOrValue.flatMap { value =>
            Try(f(value)) match {
              case Success(t) => Right(t)
              case Failure(cause) =>
                Left(wrongType(entry.key, entry.keyType, sourceValue, value, typeName, Some(cause)))
            }
          }
        }
      }
    }
}
