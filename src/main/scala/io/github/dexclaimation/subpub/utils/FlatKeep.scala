//
//  Keep.scala
//  subpub
//
//  Created by d-exclaimation on 7:48 PM.
//

package io.github.dexclaimation.subpub.utils

object FlatKeep {
  /** Keep with with tuple on the left */
  def bothL[L1, L2, R]: ((L1, L2), R) => (L1, L2, R) =
    (l, r) => (l._1, l._2, r)

  /** Keep with with tuple on the right */
  def bothR[L, R1, R2]: (L, (R1, R2)) => (L, R1, R2) =
    (l, r) => (l, r._1, r._2)

  /** Keep with with tuple on the both side */
  def both[L1, L2, R1, R2]: ((L1, L2), (R1, R2)) => (L1, L2, R1, R2) =
    (l, r) => (l._1, l._2, r._1, r._2)
}