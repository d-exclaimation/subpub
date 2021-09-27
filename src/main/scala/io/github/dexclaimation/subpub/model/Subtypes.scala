//
//  Subtypes.scala
//  subpub
//
//  Created by d-exclaimation on 7:44 PM.
//

package io.github.dexclaimation.subpub.model

import java.util.UUID

object Subtypes {
  /** ID */
  type ID = String

  /** SubPub Collision Resistance ID */
  def cuid(): ID = UUID.randomUUID().toString
}
