package nz.cri.gns.NZSHM22.opensha.inversion;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.LogicTreeBranch;

/**
 * This class provides specialisatations needed to override some UCERF3 defaults
 * in the base class.
 * 
 * @author chrisbc
 *
 */
public class NZSHM22_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {
	// this holds the various MFDs implied by the inversion fault system rupture set
	// (e.g., we need to know the sub-seismo on-fault moment rates to reduce slip
	// rates accordingly)
	private InversionTargetMFDs inversionMFDs;
	
	//TODO: fix this ....... public final static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 7.0;
	
	private static final long serialVersionUID = 1091962054533163866L;

	/**
	 * Constructor which relies on the super-class implementation
	 * 
	 * @param rupSet
	 * @param branch
	 */
	public NZSHM22_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, LogicTreeBranch branch) {
		super(rupSet, branch);
	}		
	
	/**
	 * This returns the final minimum mag for a given fault section. This uses a
	 * generic version of computeMinSeismoMagForSections() instead of the UCERF3
	 * implementation.
	 * 
	 * @param sectIndex
	 * @return
	 */
	@Override
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		if (minMagForSectArray == null) {
			// TODO: experiment to test this with higher than U3 6.0
			minMagForSectArray = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,
					MIN_MAG_FOR_SEISMOGENIC_RUPS);
		}
		return minMagForSectArray[sectIndex];
	}

	@Override
	public InversionTargetMFDs getInversionTargetMFDs() {
		if (inversionMFDs == null)
			inversionMFDs = new NZSHM22_CrustalInversionTargetMFDs(this);
		return inversionMFDs;
	}
	
	public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		this.inversionMFDs = inversionMFDs;
		return this;
	}

}
