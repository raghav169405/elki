package de.lmu.ifi.dbs.elki.algorithm.clustering;

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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.ClusterModel;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDMIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDList;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.query.range.RangeQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.IndexBasedDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.LocallyWeightedDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.logging.progress.IndefiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ChainedParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DistanceParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Provides an abstract algorithm requiring a VarianceAnalysisPreprocessor.
 * 
 * @author Arthur Zimek
 * @param <V> the type of NumberVector handled by this Algorithm
 */
public abstract class AbstractProjectedDBSCAN<R extends Clustering<Model>, V extends NumberVector<?>> extends AbstractAlgorithm<R> implements ClusteringAlgorithm<R> {
  /**
   * Parameter to specify the distance function to determine the distance
   * between database objects, must extend
   * {@link de.lmu.ifi.dbs.elki.distance.distancefunction.LocallyWeightedDistanceFunction}
   * .
   * <p>
   * Key: {@code -projdbscan.distancefunction}
   * </p>
   * <p>
   * Default value:
   * {@link de.lmu.ifi.dbs.elki.distance.distancefunction.LocallyWeightedDistanceFunction}
   * </p>
   */
  public static final OptionID OUTER_DISTANCE_FUNCTION_ID = new OptionID("projdbscan.outerdistancefunction", "Distance function to determine the distance between database objects.");

  /**
   * Parameter distance function
   */
  public static final OptionID INNER_DISTANCE_FUNCTION_ID = new OptionID("projdbscan.distancefunction", "Distance function to determine the neighbors for variance analysis.");

  /**
   * Parameter to specify the maximum radius of the neighborhood to be
   * considered, must be suitable to {@link LocallyWeightedDistanceFunction}.
   * <p>
   * Key: {@code -projdbscan.epsilon}
   * </p>
   */
  public static final OptionID EPSILON_ID = new OptionID("projdbscan.epsilon", "The maximum radius of the neighborhood to be considered.");

  /**
   * Parameter to specify the intrinsic dimensionality of the clusters to find,
   * must be an integer greater than 0.
   * <p>
   * Key: {@code -projdbscan.lambda}
   * </p>
   */
  public static final OptionID LAMBDA_ID = new OptionID("projdbscan.lambda", "The intrinsic dimensionality of the clusters to find.");

  /**
   * Parameter to specify the threshold for minimum number of points in the
   * epsilon-neighborhood of a point, must be an integer greater than 0.
   * <p>
   * Key: {@code -projdbscan.minpts}
   * </p>
   */
  public static final OptionID MINPTS_ID = new OptionID("projdbscan.minpts", "Threshold for minimum number of points in " + "the epsilon-neighborhood of a point.");

  /**
   * Holds the instance of the distance function specified by
   * {@link #INNER_DISTANCE_FUNCTION_ID}.
   */
  private LocallyWeightedDistanceFunction<V> distanceFunction;

  /**
   * Holds the value of {@link #EPSILON_ID}.
   */
  protected DoubleDistance epsilon;

  /**
   * Holds the value of {@link #LAMBDA_ID}.
   */
  private int lambda;

  /**
   * Holds the value of {@link #MINPTS_ID}.
   */
  protected int minpts = 1;

  /**
   * Holds a list of clusters found.
   */
  private List<ModifiableDBIDs> resultList;

  /**
   * Holds a set of noise.
   */
  private ModifiableDBIDs noise;

  /**
   * Holds a set of processed ids.
   */
  private ModifiableDBIDs processedIDs;

  /**
   * Constructor.
   * 
   * @param epsilon Epsilon
   * @param minpts MinPts parameter
   * @param distanceFunction Outer distance function
   * @param lambda Lambda value
   */
  public AbstractProjectedDBSCAN(DoubleDistance epsilon, int minpts, LocallyWeightedDistanceFunction<V> distanceFunction, int lambda) {
    super();
    this.epsilon = epsilon;
    this.minpts = minpts;
    this.distanceFunction = distanceFunction;
    this.lambda = lambda;
  }

  /**
   * Run the algorithm
   * 
   * @param database Database to process
   * @param relation Relation to process
   * @return Clustering result
   */
  public Clustering<Model> run(Database database, Relation<V> relation) {
    FiniteProgress objprog = getLogger().isVerbose() ? new FiniteProgress("Processing objects", relation.size(), getLogger()) : null;
    IndefiniteProgress clusprog = getLogger().isVerbose() ? new IndefiniteProgress("Number of clusters", getLogger()) : null;
    resultList = new ArrayList<>();
    noise = DBIDUtil.newHashSet();
    processedIDs = DBIDUtil.newHashSet(relation.size());

    LocallyWeightedDistanceFunction.Instance<V> distFunc = distanceFunction.instantiate(relation);
    RangeQuery<V, DoubleDistance> rangeQuery = database.getRangeQuery(distFunc);

    if(relation.size() >= minpts) {
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        if(!processedIDs.contains(iditer)) {
          expandCluster(distFunc, rangeQuery, DBIDUtil.deref(iditer), objprog, clusprog);
          if(processedIDs.size() == relation.size() && noise.size() == 0) {
            break;
          }
        }
        if(objprog != null && clusprog != null) {
          objprog.setProcessed(processedIDs.size(), getLogger());
          clusprog.setProcessed(resultList.size(), getLogger());
        }
      }
    }
    else {
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        noise.add(iditer);
        if(objprog != null && clusprog != null) {
          objprog.setProcessed(processedIDs.size(), getLogger());
          clusprog.setProcessed(resultList.size(), getLogger());
        }
      }
    }

    if(objprog != null && clusprog != null) {
      objprog.setProcessed(processedIDs.size(), getLogger());
      clusprog.setProcessed(resultList.size(), getLogger());
    }

    Clustering<Model> result = new Clustering<>(getLongResultName(), getShortResultName());
    for(Iterator<ModifiableDBIDs> resultListIter = resultList.iterator(); resultListIter.hasNext();) {
      Cluster<Model> c = new Cluster<Model>(resultListIter.next(), ClusterModel.CLUSTER);
      result.addToplevelCluster(c);
    }

    Cluster<Model> n = new Cluster<Model>(noise, true, ClusterModel.CLUSTER);
    result.addToplevelCluster(n);

    if(objprog != null && clusprog != null) {
      objprog.setProcessed(processedIDs.size(), getLogger());
      clusprog.setProcessed(resultList.size(), getLogger());
    }
    // Signal that the progress has completed.
    if(objprog != null && clusprog != null) {
      objprog.ensureCompleted(getLogger());
      clusprog.setCompleted(getLogger());
    }
    return result;
  }

  /**
   * Return the long result name.
   * 
   * @return Long name for result
   */
  public abstract String getLongResultName();

  /**
   * Return the short result name.
   * 
   * @return Short name for result
   */
  public abstract String getShortResultName();

  /**
   * ExpandCluster function of DBSCAN.
   * 
   * @param distFunc Distance query to use
   * @param rangeQuery Range query
   * @param startObjectID the object id of the database object to start the
   *        expansion with
   * @param objprog the progress object for logging the current status
   */
  protected void expandCluster(LocallyWeightedDistanceFunction.Instance<V> distFunc, RangeQuery<V, DoubleDistance> rangeQuery, DBID startObjectID, FiniteProgress objprog, IndefiniteProgress clusprog) {
    Integer corrDim = distFunc.getIndex().getLocalProjection(startObjectID).getCorrelationDimension();

    if(getLogger().isDebugging()) {
      getLogger().debugFine("EXPAND CLUSTER id = " + startObjectID + " " + corrDim + "\n#clusters: " + resultList.size());
    }

    // euclidean epsilon neighborhood < minpts OR local dimensionality >
    // lambda -> noise
    if(corrDim == null || corrDim > lambda) {
      noise.add(startObjectID);
      processedIDs.add(startObjectID);
      if(objprog != null && clusprog != null) {
        objprog.setProcessed(processedIDs.size(), getLogger());
        clusprog.setProcessed(resultList.size(), getLogger());
      }
      return;
    }

    // compute weighted epsilon neighborhood
    DistanceDBIDList<DoubleDistance> neighbors = rangeQuery.getRangeForDBID(startObjectID, epsilon);
    // neighbors < minPts -> noise
    if(neighbors.size() < minpts) {
      noise.add(startObjectID);
      processedIDs.add(startObjectID);
      if(objprog != null && clusprog != null) {
        objprog.setProcessed(processedIDs.size(), getLogger());
        clusprog.setProcessed(resultList.size(), getLogger());
      }
      return;
    }

    // try to expand the cluster
    ModifiableDBIDs currentCluster = DBIDUtil.newArray();
    ModifiableDBIDs seeds = DBIDUtil.newHashSet();
    for(DistanceDBIDListIter<DoubleDistance> seed = neighbors.iter(); seed.valid(); seed.advance()) {
      int nextID_corrDim = distFunc.getIndex().getLocalProjection(seed).getCorrelationDimension();
      // nextID is not reachable from start object
      if(nextID_corrDim > lambda) {
        continue;
      }

      if(!processedIDs.contains(seed)) {
        currentCluster.add(seed);
        processedIDs.add(seed);
        seeds.add(seed);
      }
      else if(noise.contains(seed)) {
        currentCluster.add(seed);
        noise.remove(seed);
      }
    }

    while(seeds.size() > 0) {
      DBIDMIter iter = seeds.iter();
      int corrDim_q = distFunc.getIndex().getLocalProjection(iter).getCorrelationDimension();
      // q forms no lambda-dim hyperplane
      if(corrDim_q > lambda) {
        continue;
      }

      DistanceDBIDList<DoubleDistance> reachables = rangeQuery.getRangeForDBID(iter, epsilon);
      iter.remove();

      if(reachables.size() > minpts) {
        for(DistanceDBIDListIter<DoubleDistance> r = reachables.iter(); r.valid(); r.advance()) {
          int corrDim_r = distFunc.getIndex().getLocalProjection(r).getCorrelationDimension();
          // r is not reachable from q
          if(corrDim_r > lambda) {
            continue;
          }

          boolean inNoise = noise.contains(r);
          boolean unclassified = !processedIDs.contains(r);
          if(inNoise || unclassified) {
            if(unclassified) {
              seeds.add(r);
            }
            currentCluster.add(r);
            processedIDs.add(r);
            if(inNoise) {
              noise.remove(r);
            }
            if(objprog != null && clusprog != null) {
              objprog.setProcessed(processedIDs.size(), getLogger());
              int numClusters = currentCluster.size() > minpts ? resultList.size() + 1 : resultList.size();
              clusprog.setProcessed(numClusters, getLogger());
            }
          }
        }
      }

      /*
       * if(processedIDs.size() == relation.size() && noise.size() == 0) {
       * break; }
       */
    }

    if(currentCluster.size() >= minpts) {
      resultList.add(currentCluster);
    }
    else {
      noise.addDBIDs(currentCluster);
      noise.add(startObjectID);
      processedIDs.add(startObjectID);
    }

    if(objprog != null && clusprog != null) {
      objprog.setProcessed(processedIDs.size(), getLogger());
      clusprog.setProcessed(resultList.size(), getLogger());
    }
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(distanceFunction.getInputTypeRestriction());
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public abstract static class Parameterizer<V extends NumberVector<?>, D extends Distance<D>> extends AbstractParameterizer {
    protected DistanceFunction<V, D> innerdist;

    protected D epsilon;

    protected LocallyWeightedDistanceFunction<V> outerdist;

    protected int minpts = -1;

    protected Integer lambda;

    protected void configInnerDistance(Parameterization config) {
      ObjectParameter<DistanceFunction<V, D>> innerdistP = new ObjectParameter<>(AbstractProjectedDBSCAN.INNER_DISTANCE_FUNCTION_ID, DistanceFunction.class, EuclideanDistanceFunction.class);
      if(config.grab(innerdistP)) {
        innerdist = innerdistP.instantiateClass(config);
      }
    }

    protected void configEpsilon(Parameterization config, DistanceFunction<V, D> innerdist) {
      D distanceParser = innerdist != null ? innerdist.getDistanceFactory() : null;
      DistanceParameter<D> epsilonP = new DistanceParameter<>(EPSILON_ID, distanceParser);
      if(config.grab(epsilonP)) {
        epsilon = epsilonP.getValue();
      }
    }

    protected void configMinPts(Parameterization config) {
      IntParameter minptsP = new IntParameter(MINPTS_ID);
      minptsP.addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);
      if(config.grab(minptsP)) {
        minpts = minptsP.getValue();
      }
    }

    protected void configOuterDistance(Parameterization config, D epsilon, int minpts, Class<?> preprocessorClass, DistanceFunction<V, D> innerdist) {
      ObjectParameter<LocallyWeightedDistanceFunction<V>> outerdistP = new ObjectParameter<>(OUTER_DISTANCE_FUNCTION_ID, LocallyWeightedDistanceFunction.class, LocallyWeightedDistanceFunction.class);
      if(config.grab(outerdistP)) {
        // parameters for the distance function
        ListParameterization distanceFunctionParameters = new ListParameterization();
        // distanceFunctionParameters.addFlag(PreprocessorHandler.OMIT_PREPROCESSING_ID);
        distanceFunctionParameters.addParameter(IndexBasedDistanceFunction.INDEX_ID, preprocessorClass);
        distanceFunctionParameters.addParameter(AbstractProjectedDBSCAN.INNER_DISTANCE_FUNCTION_ID, innerdist);
        distanceFunctionParameters.addParameter(AbstractProjectedDBSCAN.EPSILON_ID, epsilon);
        distanceFunctionParameters.addParameter(AbstractProjectedDBSCAN.MINPTS_ID, minpts);
        ChainedParameterization combinedConfig = new ChainedParameterization(distanceFunctionParameters, config);
        combinedConfig.errorsTo(config);
        outerdist = outerdistP.instantiateClass(combinedConfig);
      }
    }

    protected void configLambda(Parameterization config) {
      IntParameter lambdaP = new IntParameter(LAMBDA_ID);
      lambdaP.addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);
      if(config.grab(lambdaP)) {
        lambda = lambdaP.getValue();
      }
    }
  }
}