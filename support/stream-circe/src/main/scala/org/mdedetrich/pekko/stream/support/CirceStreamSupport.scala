/*
 * Copyright 2015 – 2018 Paul Horn
 * Copyright 2018 – 2021 Matthew de Detrich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mdedetrich.pekko.stream
package support

import io.circe.CursorOp.DownField
import io.circe._
import io.circe.jawn.CirceSupportParser._
import org.apache.pekko
import org.mdedetrich.pekko.json.stream.JsonStreamParser
import org.typelevel.jawn.AsyncParser

import pekko.NotUsed
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

trait CirceStreamSupport {

  def decode[A: Decoder]: Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json].map(decodeJson[A])

  def decode[A: Decoder](mode: AsyncParser.Mode): Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json](mode).map(decodeJson[A])

  def decode[A: Decoder](mode: AsyncParser.Mode, multiValue: Boolean): Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json](mode, multiValue).map(decodeJson[A])

  def encode[A](implicit A: Encoder[A], P: Printer = Printer.noSpaces): Flow[A, String, NotUsed] =
    Flow[A].map(a => P.print(A(a)))

  case class JsonParsingException(df: DecodingFailure, cursor: HCursor)
      extends Exception(errorMessage(df.history, cursor, df.message), df)

  private[mdedetrich] def decodeJson[A](json: Json)(implicit decoder: Decoder[A]): A = {
    val cursor = json.hcursor
    decoder(cursor) match {
      case Right(e) => e
      case Left(f)  => throw JsonParsingException(f, cursor)
    }
  }

  private[this] def errorMessage(hist: List[CursorOp], cursor: HCursor, typeHint: String) = {
    val ac = cursor.replay(hist)
    if (ac.failed && lastWasDownField(hist)) {
      s"The field [${CursorOp.opsToPath(hist)}] is missing."
    } else {
      s"Could not decode [${ac.focus.getOrElse(Json.Null)}] at [${CursorOp.opsToPath(hist)}] as [$typeHint]."
    }
  }

  private[this] def lastWasDownField(hist: List[CursorOp]) = hist.headOption match {
    case Some(DownField(_)) => true
    case _                  => false
  }
}

object CirceStreamSupport extends CirceStreamSupport
