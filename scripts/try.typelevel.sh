#!/usr/bin/env sh
test -e ~/.coursier/coursier || ( \
  mkdir -p ~/.coursier && \
  curl -Lso ~/.coursier/coursier https://git.io/vgvpD && \
  chmod +x ~/.coursier/coursier \
)

~/.coursier/coursier launch -q -P -M ammonite.Main \
  com.lihaoyi:ammonite_2.12.4:1.6.5 \
  -E org.scala-lang:scala-library \
  -E org.scala-lang:scala-compiler \
  -E org.scala-lang:scala-reflect \
  org.typelevel:scala-compiler:2.12.4-bin-typelevel-4 \
  org.typelevel:scala-library:2.12.4-bin-typelevel-4 \
  org.typelevel:scala-reflect:2.12.4-bin-typelevel-4 \
  is.cir:ciris-cats_2.12:0.13.0-RC1 \
  is.cir:ciris-cats-effect_2.12:0.13.0-RC1 \
  is.cir:ciris-core_2.12:0.13.0-RC1 \
  is.cir:ciris-enumeratum_2.12:0.13.0-RC1 \
  is.cir:ciris-generic_2.12:0.13.0-RC1 \
  is.cir:ciris-refined_2.12:0.13.0-RC1 \
  is.cir:ciris-spire_2.12:0.13.0-RC1 \
  is.cir:ciris-squants_2.12:0.13.0-RC1 \
  -- --predef-code "\
        interp.configureCompiler(_.settings.YliteralTypes.value = true);\
        interp.configureCompiler(_.settings.YpartialUnification.value = true);\
        import ciris.{cats => _, enumeratum => _, spire => _, squants => _, _},\
        ciris.cats._,\
        ciris.cats.effect._,\
        ciris.cats.effect.syntax._,\
        ciris.enumeratum._,\
        ciris.refined._,\
        ciris.refined.syntax._,\
        ciris.spire._,\
        ciris.squants._\
      " < /dev/tty
