package de.lmu.ifi.dbs.elki.data.type;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import de.lmu.ifi.dbs.elki.data.FeatureVector;
import de.lmu.ifi.dbs.elki.persistent.ByteBufferSerializer;

/**
 * Construct a type information for vector spaces with fixed dimensionality.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has FeatureVector
 * 
 * @param <V> Vector type
 */
public class VectorTypeInformation<V extends FeatureVector<?>> extends SimpleTypeInformation<V> {
  /**
   * Minimum dimensionality.
   */
  protected final int mindim;

  /**
   * Maximum dimensionality.
   */
  protected final int maxdim;
  
  /**
   * Constructor for an actual type.
   * 
   * @param cls base class
   * @param serializer Serializer
   * @param mindim Minimum dimensionality
   * @param maxdim Maximum dimensionality
   */
  public VectorTypeInformation(Class<? super V> cls, ByteBufferSerializer<? super V> serializer, int mindim, int maxdim) {
    super(cls, serializer);
    assert (this.mindim <= this.maxdim);
    this.mindim = mindim;
    this.maxdim = maxdim;
  }

  /**
   * Constructor for a type request.
   * 
   * @param cls base class
   * @param mindim Minimum dimensionality
   * @param maxdim Maximum dimensionality
   */
  public VectorTypeInformation(Class<? super V> cls, int mindim, int maxdim) {
    this(cls, null, mindim, maxdim);
  }

  /**
   * Constructor for a type request without dimensionality constraints.
   * 
   * @param cls Class constraint
   */
  public VectorTypeInformation(Class<? super V> cls) {
    this(cls, null, -1, Integer.MAX_VALUE);
  }

  @Override
  public boolean isAssignableFromType(TypeInformation type) {
    // This validates the base type V
    if(!super.isAssignableFromType(type)) {
      return false;
    }
    // Other type must also be a vector type
    if(!(type instanceof VectorTypeInformation)) {
      return false;
    }
    VectorTypeInformation<?> othertype = (VectorTypeInformation<?>) type;
    assert (othertype.mindim <= othertype.maxdim);
    // the other must not have a lower minimum dimensionality
    if(this.mindim > othertype.mindim) {
      return false;
    }
    // ... or a higher maximum dimensionality.
    if(othertype.maxdim > this.maxdim) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isAssignableFrom(Object other) {
    // Validate that we can assign
    if(!super.isAssignableFrom(other)) {
      return false;
    }
    // Get the object dimensionality
    final int odim = cast(other).getDimensionality();
    if(odim < mindim) {
      return false;
    }
    if(odim > maxdim) {
      return false;
    }
    return true;
  }

  /**
   * Get the minimum dimensionality of the occurring vectors.
   * 
   * @return dimensionality
   */
  public int mindim() {
    if(mindim < 0) {
      throw new UnsupportedOperationException("Requesting dimensionality for a request without defined dimensionality!");
    }
    return mindim;
  }

  /**
   * Get the maximum dimensionality of the occurring vectors.
   * 
   * @return dimensionality
   */
  public int maxdim() {
    if(maxdim == Integer.MAX_VALUE) {
      throw new UnsupportedOperationException("Requesting dimensionality for a request without defined dimensionality!");
    }
    return maxdim;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(super.toString());
    buf.append(",variable");
    if(mindim >= 0) {
      buf.append(",mindim=").append(mindim);
    }
    if(maxdim < Integer.MAX_VALUE) {
      buf.append(",maxdim=").append(maxdim);
    }
    return buf.toString();
  }
}