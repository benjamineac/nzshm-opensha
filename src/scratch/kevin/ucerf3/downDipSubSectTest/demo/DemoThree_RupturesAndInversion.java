package scratch.kevin.ucerf3.downDipSubSectTest.demo;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dom4j.DocumentException;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.kevin.ucerf3.downDipSubSectTest.DownDipSubSectBuilder;
import scratch.kevin.ucerf3.downDipSubSectTest.DownDipTestPermutationStrategy;
import scratch.kevin.ucerf3.downDipSubSectTest.RectangularityFilter;

/* 
* Based on Kevins work 
*
* https://github.com/opensha/opensha-dev/blob/master/src/scratch/kevin/ucerf3/downDipSubSectTest/DownDipTestRupSetBuilder.java
*
*/
public class DemoThree_RupturesAndInversion {
	

	static DownDipSubSectBuilder downDipBuilder;
	
	public static void main(String[] args) throws DocumentException, IOException {
		
		boolean runInversion = true;
		long inversionMins = 1; // run it for this many minutes
		
		// maximum sub section length (in units of DDW)
		double maxSubSectionLength = 0.5;
		// max distance for linking multi fault ruptures, km
		//double maxDistance = 0.5d;
	
		//File outputFile = new File("/tmp/rupSetLowerNIAndInterface30km.zip");

		File rupSetFile = new File("/tmp/down_dip_sub_sect_rup_set.zip");
		File solFile = new File("/tmp/down_dip_sub_sect_sol.zip");
	
		File fsdFile = new File("./data/FaultModels/cfm_test.xml");
		
		// load in the fault section data ("parent sections")
		List<FaultSection> fsd = FaultModels.loadStoredFaultSections(fsdFile);
		
		Stream<Integer> myStream01 = Stream.of(83, 84, 85, 86, 87);
		Collection<Integer> sectsWairarapa = myStream01.collect(Collectors.toCollection(ArrayList::new));

		Stream<Integer> myStream02 = Stream.of(89, 90, 91, 92, 93);
		Collection<Integer> sectsWellington = myStream02.collect(Collectors.toCollection(ArrayList::new));
		
		List<Integer> sectsToKeep = Lists.newArrayList(Iterables.concat(sectsWairarapa, sectsWellington));

   	    if (sectsToKeep != null && !sectsToKeep.isEmpty()) {
		 	System.out.println("Only keeping these parent fault sections: "
		 			+Joiner.on(",").join(sectsToKeep));
		 	// iterate backwards as we will be removing from the list
		 	for (int i=fsd.size(); --i>=0;)
		 		if (!sectsToKeep.contains(fsd.get(i).getSectionId()))
		 			fsd.remove(i);
		 }		
		
		// build the subsections
		List<FaultSection> subSections = new ArrayList<>();
		int sectIndex = 0;
		for (FaultSection parentSect : fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();
		}
		
		System.out.println(subSections.size()+" Sub Sections");
		
		String sectName = "Interface Fault 30km";
//		int sectID = 0;
		int startID = subSections.size();
		
		FaultSection interfaceParentSection = new FaultSectionPrefData();
		interfaceParentSection.setSectionId(10000);
		interfaceParentSection.setSectionName(sectName);
				
		File initialFile = new File("./data/FaultModels/subduction_tile_parameters_30.csv");
	    InputStream inputStream = new FileInputStream(initialFile);
		downDipBuilder = new DownDipSubSectBuilder(sectName, interfaceParentSection, startID, inputStream);
		
		// Add the interface subsections
		subSections.addAll(downDipBuilder.getSubSectsList());
		
		System.out.println("Have "+subSections.size()+" sub-sections in total");		
		
		for (int s=0; s<subSections.size(); s++)
			Preconditions.checkState(subSections.get(s).getSectionId() == s,
				"section at index %s has ID %s", s, subSections.get(s).getSectionId());
		
		// instantiate plausibility filters
		List<PlausibilityFilter> filters = new ArrayList<>();
		int minDimension = 1; // minimum numer of rows or columns
		double maxAspectRatio = 5d; // max aspect ratio of rows/cols or cols/rows
		filters.add(new RectangularityFilter(downDipBuilder, minDimension, maxAspectRatio));
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		
		// this creates rectangular permutations only for our down-dip fault to speed up rupture building
		ClusterPermutationStrategy permutationStrategy = new DownDipTestPermutationStrategy(downDipBuilder);
		// connection strategy: parent faults connect at closest point, and only when dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(5d);
		int maxNumSplays = 0; // don't allow any splays
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(subSections, connectionStrategy,
				distAzCalc, filters, maxNumSplays);
		
		List<ClusterRupture> ruptures = builder.build(permutationStrategy);
		
		System.out.println("Built "+ruptures.size()+" total ruptures");
		
		
		MySlipEnabledRupSet rupSet = new MySlipEnabledRupSet(ruptures, subSections,
				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
		
		FaultSystemIO.writeRupSet(rupSet, rupSetFile);
		
		if (runInversion) {
			List<InversionConstraint> constraints = new ArrayList<>();
			
			/*
			 * Slip rate constraints
			 */
			// For SlipRateConstraintWeightingType.NORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if UNNORMALIZED!
			double slipRateConstraintWt_normalized = 1;
			// For SlipRateConstraintWeightingType.UNNORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if NORMALIZED!
			double slipRateConstraintWt_unnormalized = 100;
			// If normalized, slip rate misfit is % difference for each section (recommended since it helps fit slow-moving faults).
			// If unnormalized, misfit is absolute difference.
			// BOTH includes both normalized and unnormalized constraints.
			SlipRateConstraintWeightingType slipRateWeighting = SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)
			constraints.add(new SlipRateInversionConstraint(slipRateConstraintWt_normalized, slipRateConstraintWt_unnormalized,
					slipRateWeighting, rupSet, rupSet.getSlipRateForAllSections()));
			
			/*
			 * MFD constraints
			 */
			double totalRateM5 = 5d; // expected number of M>=5's per year
			double bValue = 1d; // G-R b-value
			// magnitude to switch from MFD equality to MFD inequality
			double mfdTransitionMag = 7.85;
			double mfdEqualityConstraintWt = 10;
			double mfdInequalityConstraintWt = 1000;
			int mfdNum = 40;
			double mfdMin = 5.05d;
			double mfdMax = 8.95;
			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdMin, mfdMax, mfdNum);
			int transitionIndex = mfd.getClosestXIndex(mfdTransitionMag);
			// snap it to the discretization if it wasn't already
			mfdTransitionMag = mfd.getX(transitionIndex);
			Preconditions.checkState(transitionIndex >= 0);
			GutenbergRichterMagFreqDist equalityMFD = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);
			MFD_InversionConstraint equalityConstr = new MFD_InversionConstraint(equalityMFD, null);
			GutenbergRichterMagFreqDist inequalityMFD = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size()-equalityMFD.size());
			MFD_InversionConstraint inequalityConstr = new MFD_InversionConstraint(inequalityMFD, null);
			
			constraints.add(new MFDEqualityInversionConstraint(rupSet, mfdEqualityConstraintWt,
					Lists.newArrayList(equalityConstr), null));
			constraints.add(new MFDInequalityInversionConstraint(rupSet, mfdInequalityConstraintWt,
					Lists.newArrayList(inequalityConstr)));
			
			// weight of entropy-maximization constraint (not used in UCERF3)
			double smoothnessWt = 0;
			
			/*
			 * Build inversion inputs
			 */
			InversionInputGenerator inputGen = new InversionInputGenerator(rupSet, constraints);
			
			inputGen.generateInputs(true);
			// column compress it for fast annealing
			inputGen.columnCompress();
			
			// inversion completion criteria (how long it will run)
			CompletionCriteria criteria = TimeCompletionCriteria.getInMinutes(inversionMins);
			
			// Bring up window to track progress
			// criteria = new ProgressTrackingCompletionCriteria(criteria, 0.1);

			// this will use all available processors
			int numThreads = Runtime.getRuntime().availableProcessors();

			// this is the "sub completion criteria" - the amount of time (or iterations) between synchronization
			CompletionCriteria subCompetionCriteria = TimeCompletionCriteria.getInSeconds(1); // 1 second;
			
			ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
					inputGen.getInitialSolution(), smoothnessWt, inputGen.getA_ineq(), inputGen.getD_ineq(),
					inputGen.getWaterLevelRates(), numThreads, subCompetionCriteria);
			tsa.setConstraintRanges(inputGen.getConstraintRowRanges());
			
			tsa.iterate(criteria);
			
			// now assemble the solution
			double[] solution_raw = tsa.getBestSolution();
			
			// adjust for minimum rates if applicable
			double[] solution_adjusted = inputGen.adjustSolutionForWaterLevel(solution_raw);
			
			Map<ConstraintRange, Double> energies = tsa.getEnergies();
			if (energies != null) {
				System.out.println("Final energies:");
				for (ConstraintRange range : energies.keySet())
					System.out.println("\t"+range.name+": "+energies.get(range).floatValue());
			}
			
			// now write out the solution
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, solution_adjusted);
			FaultSystemIO.writeSol(sol, solFile);
		}		
		
		System.exit(0);
	}
	
	private static class MySlipEnabledRupSet extends SlipAlongRuptureModelRupSet {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = -4984738430816137976L;
		private double[] rupAveSlips;
		
		public MySlipEnabledRupSet(List<ClusterRupture> ruptures, List<FaultSection> subSections,
				ScalingRelationships scale, SlipAlongRuptureModels slipAlongModel) {
			super(slipAlongModel);
			
			// build a rupture set (doing this manually instead of creating an inversion fault system rup set,
			// mostly as a demonstration)
			double[] sectSlipRates = new double[subSections.size()];
			double[] sectAreasReduced = new double[subSections.size()];
			double[] sectAreasOrig = new double[subSections.size()];
			for (int s=0; s<sectSlipRates.length; s++) {
				FaultSection sect = subSections.get(s);
				sectAreasReduced[s] = sect.getArea(true);
				sectAreasOrig[s] = sect.getArea(false);
				sectSlipRates[s] = sect.getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
			}
			
			double[] rupMags = new double[ruptures.size()];
			double[] rupRakes = new double[ruptures.size()];
			double[] rupAreas = new double[ruptures.size()];
			double[] rupLengths = new double[ruptures.size()];
			rupAveSlips = new double[ruptures.size()];
			List<List<Integer>> rupsIDsList = new ArrayList<>();
			for (int r=0; r<ruptures.size(); r++) {
				ClusterRupture rup = ruptures.get(r);
				List<FaultSection> rupSects = rup.buildOrderedSectionList();
				List<Integer> sectIDs = new ArrayList<>();
				double totLength = 0d;
				double totArea = 0d;
				double totOrigArea = 0d; // not reduced for aseismicity
				List<Double> sectAreas = new ArrayList<>();
				List<Double> sectRakes = new ArrayList<>();
				for (FaultSection sect : rupSects) {
					sectIDs.add(sect.getSectionId());
					double length = sect.getTraceLength()*1e3;	// km --> m
					totLength += length;
					double area = sectAreasReduced[sect.getSectionId()];	// sq-m
					totArea += area;
					totOrigArea += sectAreasOrig[sect.getSectionId()];	// sq-m
					sectAreas.add(area);
					sectRakes.add(sect.getAveRake());
				}
				rupAreas[r] = totArea;
				rupLengths[r] = totLength;
				rupRakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(sectAreas, sectRakes));
				double origDDW = totOrigArea/totLength;
				rupMags[r] = scale.getMag(totArea, origDDW);
				rupsIDsList.add(sectIDs);
				rupAveSlips[r] = scale.getAveSlip(totArea, totLength, origDDW);
			}
			
			String info = "Test down-dip subsectioning rup set";
			
			init(subSections, sectSlipRates, null, sectAreasReduced,
					rupsIDsList, rupMags, rupRakes, rupAreas, rupLengths, info);
		}
	
		@Override
		public double getAveSlipForRup(int rupIndex) {
			return rupAveSlips[rupIndex];
		}
	
		@Override
		public double[] getAveSlipForAllRups() {
			return rupAveSlips;
		}
		
	}

}