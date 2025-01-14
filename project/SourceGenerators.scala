import sbt._

object SourceGenerators extends AutoPlugin {
  object autoImport {
    def generateSources(
      sourcesRoot: File,
      testSourcesRoot: File,
      rootPackage: String
    ): Seq[File] = {
      generateConfigResultClasses(sourcesRoot, rootPackage) ++
        generateLoadConfigs(sourcesRoot, rootPackage) ++
        generateLoadConfigsSpec(testSourcesRoot, rootPackage)
    }
  }

  val autoGeneratedNotice: String =
    """
      |/**
      |  * Generated using sbt source generators.
      |  * You should not modify this file directly.
      |  */
    """.stripMargin.trim

  val maximumNumberOfParams: Int = 22

  /**
    * Generates: {{{ A1${sep}A2$sep...A$n$sep }}}
    */
  def typeParams(n: Int, sep: String = ", "): String =
    (1 to n).map(typeParam).mkString(sep)

  /**
    * Generates: {{{ A$n }}}
    */
  def typeParam(n: Int): String = s"A$n"

  /**
    * Generates: {{{ $prefix1${sep}$prefix2$sep...$prefix$n$sep }}}
    */
  def valueParams(n: Int, sep: String = ", ", prefix: String = "a"): String =
    (1 to n).map(i => valueParam(i, prefix)).mkString(sep)

  /**
    * Generates: {{{ $prefix$n }}}
    */
  def valueParam(n: Int, prefix: String = "a"): String = s"$prefix$n"

  /**
    * Generates: {{{ a1: ${typeName(1)}, a2: ${typeName(2)},..., a$n: ${typeName(n)} }}}
    */
  def args(n: Int, typeName: Int => String): String =
    (1 to n).map(i => s"${valueParam(i)}: ${typeName(i)}").mkString(", ")

  def generateLoadConfigs(sourcesRoot: File, rootPackage: String): Seq[File] = {
    val defs =
      (2 until maximumNumberOfParams)
        .map { current =>
          val params = typeParams(current)
          val firstArgs = args(current, arg => s"ConfigResult[F, ${typeParam(arg)}]")

          val loadConfigSecondArgs = s"f: (${typeParams(current)}) => Z"
          val withValuesSecondArgs = s"f: (${typeParams(current)}) => ConfigResult[F, Z]"

          val valueParamsDoc = (1 to current)
            .map(n => s"  * @param ${valueParam(n)} configuration result $n")
            .mkString("\n")
          val typeParamsDoc = (1 to current)
            .map(n => s"  * @tparam ${typeParam(n)} the type for configuration result $n")
            .mkString("\n")

          val loadConfigDoc =
            s"""
              |/**
              |  * Loads a configuration using the $current specified [[ConfigResult]]s.
              |  * Deals with error accumulation if there are any errors in the
              |  * provided [[ConfigResult]]s.
              |  *
              |$valueParamsDoc
              |  * @param f the function to create the configuration
              |$typeParamsDoc
              |  * @tparam F the [[ConfigResult]] context
              |  * @tparam Z the type of the configuration
              |  * @return the configuration or errors
              |  */
            """.stripMargin.trim.split("\n")

          val withValuesDoc =
            s"""
               |/**
               |  * Defines a requirement on $current [[ConfigResult]]s in order to be
               |  * able to load a configuration. The function wraps `loadConfig`
               |  * functions, requiring the provided [[ConfigResult]]s to be
               |  * available in order to use the `loadConfig` functions.
               |  *
               |  * Deals with error accumulation if there are any errors in
               |  * the provided [[ConfigResult]]s.
               |  *
               |$valueParamsDoc
               |  * @param f the function to create the configuration
               |$typeParamsDoc
               |  * @tparam F the [[ConfigResult]] context
               |  * @tparam Z the type of the configuration
               |  * @return the configuration or errors
               |  */
             """.stripMargin.trim.split("\n")

          {
            loadConfigDoc ++
              Seq(
                s"def loadConfig[F[_]: Apply, $params, Z]($firstArgs)($loadConfigSecondArgs): ConfigResult[F, Z] =",
                s"  ConfigResult((${valueParams(current, sep = " append ")}).result.map(_.map(f.tupled)))",
                ""
              ) ++
              withValuesDoc ++
              Seq(
                s"def withValues[F[_]: Monad, $params, Z]($firstArgs)($withValuesSecondArgs): ConfigResult[F, Z] =",
                s"  ConfigResult((${valueParams(current, sep = " append ")}).result.flatMap {",
                "     case Left(errors) => left[Z](errors).pure[F]",
                "     case Right(values) => f.tupled.apply(values).result",
                "   })"
              )
          }.map("  " + _).mkString("\n")
        }
        .mkString("\n\n")

    val content =
      s"""
        |// format: off
        |
        |$autoGeneratedNotice
        |
        |package $rootPackage
        |
        |import $rootPackage.api._
        |import $rootPackage.api.syntax._
        |import $rootPackage.ConfigErrors.{left, right}
        |
        |private[$rootPackage] class LoadConfigs {
        |
        |  /**
        |    * Wraps the specified value in a `ConfigResult[F, Z]`. Useful when
        |    * you want to use a static configuration inside a `withValues`
        |    * block, requiring you to wrap it with this function.
        |    *
        |    * @param z the value to wrap
        |    * @tparam F the [[ConfigResult]] context
        |    * @tparam Z the type of the value to wrap
        |    * @return the value wrapped in a `ConfigResult[F, Z]`
        |    */
        |  def loadConfig[F[_]: Applicative, Z](z: Z): ConfigResult[F, Z] =
        |    ConfigResult(right(z).pure[F])
        |
        |  /**
        |    * Loads a configuration using the specified [[ConfigResult]].
        |    *
        |    * @param a1 the configuration result
        |    * @param f the function to create the configuration
        |    * @tparam F the [[ConfigResult]] context
        |    * @tparam A1 the type of the configuration result
        |    * @tparam Z the type of the configuration
        |    * @return the configuration or errors
        |    */
        |  def loadConfig[F[_]: Apply, A1, Z](a1: ConfigResult[F, A1])(f: A1 => Z): ConfigResult[F, Z] =
        |    ConfigResult(a1.result.map(_.map(f)))
        |
        |  /**
        |    * Defines a requirement on a single [[ConfigResult]] in order to be
        |    * able to load a configuration. The function wraps `loadConfig`
        |    * functions, requiring the provided [[ConfigResult]] to be
        |    * available in order to use the `loadConfig` functions.
        |    *
        |    * @param a1 the configuration result
        |    * @param f the function to create the configuration
        |    * @tparam F the [[ConfigResult]] context
        |    * @tparam A1 the type of the configuration result
        |    * @tparam Z the type of the configuration
        |    * @return the configuration or errors
        |    */
        |  def withValue[F[_]: Monad, A1, Z](a1: ConfigResult[F, A1])(f: A1 => ConfigResult[F, Z]): ConfigResult[F, Z] =
        |    withValues(a1)(f)
        |
        |  /**
        |    * Defines a requirement on a single [[ConfigResult]] in order to be
        |    * able to load a configuration. The function wraps any `loadConfig`
        |    * functions, requiring the provided [[ConfigResult]] to be
        |    * available in order to use the `loadConfig` functions.
        |    *
        |    * @param a1 the configuration result
        |    * @param f the function to create the configuration
        |    * @tparam F the [[ConfigResult]] context
        |    * @tparam A1 the type of the configuration result
        |    * @tparam Z the type of the configuration
        |    * @return the configuration or errors
        |    */
        |  def withValues[F[_]: Monad, A1, Z](a1: ConfigResult[F, A1])(f: A1 => ConfigResult[F, Z]): ConfigResult[F, Z] =
        |    ConfigResult(a1.result.flatMap {
        |      case Left(errors) => left[Z](errors).pure[F]
        |      case Right(value) => f(value).result
        |    })
        |
        |$defs
        |}
      """.stripMargin.trim + "\n"

    val output = sourcesRoot / rootPackage / "LoadConfigs.scala"
    IO.write(output, content)
    Seq(output)
  }

  def generateConfigResultClasses(sourcesRoot: File, rootPackage: String): Seq[File] = {
    val classes = (2 until maximumNumberOfParams)
      .map { current =>
        val next = current + 1
        val nextTypeParam = typeParam(next)
        val currentTypeParams = typeParams(current)

        val defs =
          if (current == maximumNumberOfParams - 1) ""
          else {
            // format: off
            s"""
               |{
               |  def append[$nextTypeParam](next: ConfigResult[F, $nextTypeParam]): ConfigResult$next[F, ${typeParams(next)}] =
               |    new ConfigResult$next((result product next.result).map {
               |      case (Right((${valueParams(current)})), Right(${valueParam(next)})) => Right((${valueParams(next)}))
               |      case (Left(errors1), Right(_)) => Left(errors1)
               |      case (Right(_), Left(errors2)) => Left(errors2)
               |      case (Left(errors1), Left(errors2)) => Left(errors1 combine errors2)
               |    })
               |  }
               """.stripMargin.trim
            // format: on
          }

        val signature =
          s"private[$rootPackage] final class ConfigResult$current[F[_]: Apply, $currentTypeParams](val result: F[Either[ConfigErrors, ($currentTypeParams)]])"

        s"$signature $defs"
      }
      .mkString("\n\n")

    val content =
      s"""
         |// format: off
         |
         |$autoGeneratedNotice
         |
         |package $rootPackage
         |
         |import $rootPackage.api._
         |import $rootPackage.api.syntax._
         |
         |$classes
       """.stripMargin.trim + "\n"

    val output = sourcesRoot / rootPackage / "ConfigResults.scala"
    IO.write(output, content)
    Seq(output)
  }

  def generateLoadConfigsSpec(testSourcesRoot: File, rootPackage: String): Seq[File] = {

    def reads(n: Int, from: Int = 1, typeName: String = "String"): String =
      (from to n).map(i => s"""read[$typeName]("key$i")""").mkString(", ")

    def readsFirstMissing(n: Int): String =
      Seq(
        """read[String]("akey1")""",
        reads(n, from = 2)
      ).filter(_.nonEmpty).mkString(", ")

    def readsLastOneMissing(n: Int): String =
      Seq(
        reads(n - 1),
        s"""read[String]("akey$n")"""
      ).filter(_.nonEmpty).mkString(", ")

    def readsFirstTypeWrong(n: Int): String =
      Seq(
        """read[Int]("key1")""",
        reads(n, from = 2)
      ).filter(_.nonEmpty).mkString(", ")

    def readsLastTypeWrong(n: Int): String =
      Seq(
        reads(n - 1),
        s"""read[Int]("key$n")"""
      ).filter(_.nonEmpty).mkString(", ")

    def readsAllTypesWrong(n: Int): String =
      reads(n, typeName = "Int")

    def identity(n: Int): String =
      s"(${valueParams(n)}) => (${valueParams(n)})"

    def values(n: Int): String =
      (1 to n).map(i => s""""value$i"""").mkString(", ")

    def testsWithParams(n: Int): String = {
      val tests =
        if (n == 0) {
          s"""
            |"loading 0 keys" should {
            |  "always be able to load" in {
            |    forAll { int: Int =>
            |      loadConfig(int).result shouldBe Right(int)
            |    }
            |  }
            |}
          """.stripMargin
        } else {
          val withValueFunctions =
            if (n == 1) List("withValues", "withValue")
            else List("withValues")

          // format: off
          s"""
             |"loading $n keys" should {
             |  "be able to load" in {
             |    loadConfig(${reads(n)})(${identity(n)}).result shouldBe Right((${values(n)}))
             |  }
             |
             |  "be able to load values" in {
             |    ${withValueFunctions.map(_ + s"""(${reads(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe Right((${values(n)}))""").mkString("\n    ")}
             |  }
             |
             |  "fail to load if the first one is missing" in {
             |    loadConfig(${readsFirstMissing(n)})(${identity(n)}).result shouldBe a[Left[_, _]]
             |  }
             |
             |  "fail to load values if the first one is missing" in {
             |    ${withValueFunctions.map(_ + s"""(${readsFirstMissing(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe a[Left[_, _]]""").mkString("\n    ")}
             |  }
             |
             |  "fail to load if the last one is missing in" in {
             |    loadConfig(${readsLastOneMissing(n)})(${identity(n)}).result shouldBe a[Left[_, _]]
             |  }
             |
             |  "fail to load values if the last one is missing" in {
             |    ${withValueFunctions.map(_ + s"""(${readsLastOneMissing(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe a[Left[_, _]]""").mkString("\n    ")}
             |  }
             |
             |  "fail to load if the first type is wrong" in {
             |    loadConfig(${readsFirstTypeWrong(n)})(${identity(n)}).result shouldBe a[Left[_, _]]
             |  }
             |
             |  "fail to load values if the first type is wrong" in {
             |    ${withValueFunctions.map(_ + s"""(${readsFirstTypeWrong(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe a[Left[_, _]]""").mkString("\n    ")}
             |  }
             |
             |  "fail to load if the last type is wrong" in {
             |    loadConfig(${readsLastTypeWrong(n)})(${identity(n)}).result shouldBe a[Left[_, _]]
             |  }
             |
             |  "fail to load values if the last type is wrong" in {
             |    ${withValueFunctions.map(_ + s"""(${readsLastTypeWrong(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe a[Left[_, _]]""").mkString("\n    ")}
             |  }
             |
             |  "fail to load and accumulate the errors" in {
             |    loadConfig(${readsAllTypesWrong(n)})(${identity(n)}).result.left.map(_.size) shouldBe Left($n)
             |  }
             |
             |  "fail to load values and accumulate the errors" in {
             |    ${withValueFunctions.map(_ + s"""(${readsAllTypesWrong(n)})((${valueParams(n, prefix = "b")}) => loadConfig(${reads(n)})(${identity(n)})).result shouldBe a[Left[_, _]]""").mkString("\n    ")}
             |  }
             |}
           """.stripMargin
          // format: on
        }

      tests.trim.split('\n').map((" " * 6) + _).mkString("\n")
    }

    val tests: String =
      (0 until maximumNumberOfParams)
        .map(testsWithParams)
        .mkString("\n\n")

    val content =
      s"""
        |// format: off
        |
        |$autoGeneratedNotice
        |
        |package $rootPackage
        |
        |import $rootPackage.api._
        |
        |final class LoadConfigsSpec extends PropertySpec {
        |  "LoadConfigs" when {
        |    "loading configurations" when {
        |      implicit val source: ConfigSource[Id, String, String] = sourceWith("key1" -> "value1", "key2" -> "value2", "key3" -> "value3", "key4" -> "value4", "key5" -> "value5", "key6" -> "value6", "key7" -> "value7", "key8" -> "value8", "key9" -> "value9", "key10" -> "value10", "key11" -> "value11", "key12" -> "value12", "key13" -> "value13", "key14" -> "value14", "key15" -> "value15", "key16" -> "value16", "key17" -> "value17", "key18" -> "value18", "key19" -> "value19", "key20" -> "value20", "key21" -> "value21", "key22" -> "value22")
        |
        |      def read[Value](key: String)(implicit decoder: ConfigDecoder[String, Value]): ConfigEntry[Id, String, String, Value] =
        |        source.read(key).decodeValue[Value]
        |
        |$tests
        |    }
        |  }
        |}
      """.stripMargin.trim

    val output = testSourcesRoot / rootPackage / "LoadConfigsSpec.scala"
    IO.write(output, content)
    Seq(output)
  }
}
