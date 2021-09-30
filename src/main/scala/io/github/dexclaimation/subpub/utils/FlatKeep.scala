//
//  Keep.scala
//  subpub
//
//  Created by d-exclaimation on 7:48 PM.
//

package io.github.dexclaimation.subpub.utils

object FlatKeep {
  def bothR[R1, R2, L]: ((R1, R2), L) => (R1, R2, L) = (r, l) => (r._1, r._2, l)

  def bothL[R, L1, L2]: (R, (L1, L2)) => (R, L1, L2) = (r, l) => (r, l._1, l._2)

  def both[R1, R2, L1, L2]: ((R1, R2), (L1, L2)) => (R1, R2, L1, L2) = (r, l) => (r._1, r._2, l._1, l._2)
}