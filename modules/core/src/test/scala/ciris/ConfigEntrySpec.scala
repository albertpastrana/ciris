package ciris

import ciris.api._

final class ConfigEntrySpec extends PropertySpec {
  "ConfigEntry" when {
    "using toString" should {
      "include the key and keyType" in {
        forAll { value: String =>
          existingEntry(value).toString shouldBe
            "ConfigEntry(key, ConfigKeyType(test key))"
        }
      }
    }

    "using withDescription" should {
      "set the description" in {
        forAll { value: String =>
          existingEntry(value).withDescription(value).description shouldBe Some(value)
        }
      }

      "include the description in the output of toString" in {
        forAll { value: String =>
          existingEntry(value).withDescription(value).toString shouldBe
            s"ConfigEntry(key, ConfigKeyType(test key), $value)"
        }
      }
    }

    "using toStringWithValue" should {
      "include the value" in {
        forAll { value: String =>
          existingEntry(value).toStringWithValue shouldBe
            s"ConfigEntry(key, ConfigKeyType(test key), Right($value))"
        }
      }
    }

    "using toStringWithValues" should {
      "include the key, keyType, and value" in {
        forAll { value: String =>
          existingEntry(value).toStringWithValues shouldBe
            s"ConfigEntry(key, ConfigKeyType(test key), Right($value))"
        }
      }

      "include the sourceValue as well if different from the value" in {
        forAll { value: String =>
          existingEntry(value)
            .mapValue(_ + "2")
            .toStringWithValues shouldBe {
            s"ConfigEntry(key, ConfigKeyType(test key), Right($value), Right(${value}2))"
          }
        }
      }
    }

    "using toStringWithResult" should {
      "include the key, keyType, and result" in {
        forAll { value: String =>
          existingEntry(value).toStringWithResult shouldBe
            s"ConfigEntry(key, ConfigKeyType(test key), Right($value))"
        }
      }

      "include the key, keyType, and errors" in {
        nonExistingEntry.toStringWithResult shouldBe
          s"ConfigEntry(key, ConfigKeyType(test key), Left(ConfigErrors(MissingKey(key, ConfigKeyType(test key)))))"
      }
    }

    "using withValue" should {
      "replace the existing value" in {
        existingEntry("value").withValue(Right("value2")).value shouldBe Right("value2")
      }
    }

    "using flatMapValue" should {
      "keep this error" in {
        nonExistingEntry.flatMapValue(_ => Right("value")).value shouldBe a[Left[_, _]]
      }

      "replace the value" in {
        existingEntry("value").flatMapValue(_ => Right("value2")).value shouldBe Right("value2")
      }
    }

    "using mapValue" when {
      "the value was read successfully" should {
        "apply the function on the value" in {
          forAll { value: String =>
            val f: String => String = _.take(1)
            val entry = existingEntry(value).mapValue(f)
            entry.value shouldBe Right(f(value))
          }
        }
      }

      "the value was not read successfully" should {
        "leave the value as it is" in {
          val entry = nonExistingEntry.mapValue(_.take(1))
          entry.value shouldBe nonExistingEntry.value
        }
      }
    }

    "using transformF" when {
      "transforming Id to Id" should {
        "leave the values unmodified" in {
          val entry = existingEntry("value")
          val transformed = entry.transformF[Id]

          transformed.value shouldBe entry.value
          transformed.sourceValue shouldBe entry.sourceValue
        }
      }
    }

    "using orElse" when {
      "this value was read successfully" should {
        "use this value and not the other one" in {
          existingEntry("value")
            .orElse(existingEntry("value2"))
            .value shouldBe Right("value")
        }
      }

      "this value was not read successfully" when {
        "the other value was read successfully" should {
          "use the other value" in {
            nonExistingEntry
              .orElse(existingEntry("value2"))
              .value shouldBe Right("value2")
          }
        }

        "the other value was not read successfully" should {
          "accumulate the errors of both values" in {
            val (first, second) = (nonExistingEntry, nonExistingEntry)
            val (error1, error2) = (
              first.value.swap.getOrElse(throw new IllegalArgumentException),
              second.value.swap.getOrElse(throw new IllegalArgumentException)
            )
            first.orElse(second).value.left.get.message shouldBe (error1 combine error2).message
          }
        }
      }
    }
  }
}
