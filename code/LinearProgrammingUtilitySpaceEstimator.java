package project;

import java.util.List;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Objective;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.OutcomeComparison;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import genius.core.utility.EvaluatorDiscrete;
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

public class LinearProgrammingUtilitySpaceEstimator {
	private AdditiveUtilitySpace u;
	private int constraintNum = 1;
	List<String> variableName = new ArrayList<String>();
	double[] variableValue;
	int variableXSize = 0;

	public LinearProgrammingUtilitySpaceEstimator(Domain d) {
		int noIssues = d.getIssues().size();
		Map<Objective, Evaluator> evaluatorMap = new HashMap<Objective, Evaluator>();
		
		for (Issue i : d.getIssues()) {
			IssueDiscrete issue = (IssueDiscrete) i;
			EvaluatorDiscrete evaluator = new EvaluatorDiscrete();
			evaluator.setWeight(1.0 / noIssues);
			for (ValueDiscrete value : issue.getValues()) {
				evaluator.setEvaluationDouble(value, 0.0);
				variableName.add("w"+i.getNumber()+"x"+value.toString());
				variableXSize++;
			}
			
			evaluatorMap.put(issue, evaluator);
		}

		u = new AdditiveUtilitySpace(d, evaluatorMap);
	}

	public void setWeight(Issue i, double weight) {
		EvaluatorDiscrete evaluator = (EvaluatorDiscrete) u.getEvaluator(i);
		evaluator.setWeight(weight);
	}

	public void setUtility(Issue i, ValueDiscrete v, double value) {
		EvaluatorDiscrete evaluator = (EvaluatorDiscrete) u.getEvaluator(i);
		if (evaluator == null) {
			evaluator = new EvaluatorDiscrete();
			u.addEvaluator(i, evaluator);
		}
		evaluator.setEvaluationDouble(v, value);
	}

	public double getUtility(Issue i, ValueDiscrete v) {
		EvaluatorDiscrete evaluator = (EvaluatorDiscrete) u.getEvaluator(i);
		return evaluator.getDoubleValue(v);
	}

	public void estimateUsingBidRanks(BidRanking r) {
		double[] solution = getLinearSolution(r);
		System.out.println("Solution:"+Arrays.toString(solution));
		//adjusting negative number
		double negNum = 0.0;
		for (int index = 0 ; index < variableXSize ; index++) {
			if(negNum > solution[index]) {
				negNum = solution[index];
			}
		}
		if(negNum<0) {
			for (int index = 0 ; index < variableXSize ; index++) {
				solution[index] = solution[index] + Math.abs(negNum);
			}
			System.out.println("Negative x(s) found >> Solution:"+Arrays.toString(solution));
		}
		
		for (int index = 0 ; index < variableXSize ; index++) {
			for (Issue i : getIssues()) {
				IssueDiscrete issue = (IssueDiscrete) i;
				for (ValueDiscrete value : issue.getValues()) {
					int indexVar = variableName.indexOf("w"+i.getNumber()+"x"+value.toString());
					if(solution[indexVar]<0) {
						System.out.println(solution[indexVar]);
					}
					setUtility(i,value,solution[indexVar]);
				}
			}
		}
		
		normalizeWeightsByMaxValues();
	}
	
	private double[] getLinearSolution(BidRanking r) {
		variableValue = new double[variableXSize+r.getAmountOfComparisons()];
		
		int index;
		//set initial for constraint
		for(index = 0 ; index < variableValue.length ; index++) {
		variableValue[index] = 0.0;
		}
		
		//objective function
		double[] zValue = variableValue.clone();
		for(index = variableXSize ; index < variableValue.length ; index++) {
			zValue[index] = 1.0;
		}
		
		LinearProgram lp = new LinearProgram(zValue);
		System.out.println(variableName);
		System.out.println("Objective value"+Arrays.toString(zValue));
		
		//create x variable constraints
		for(index = 0 ; index < variableXSize ; index++) {
			double[] createConstraints = variableValue.clone();
			createConstraints[index] = 1.0;
			lp.addConstraint(new LinearBiggerThanEqualsConstraint(createConstraints, 0.0, "c"+constraintNum));
			//System.out.println("C"+Arrays.toString(createConstraints));
			constraintNum++;
		}
		
		// z constraint + comparing constraint
		int zNum = 1;
		index = variableXSize;
		for (OutcomeComparison comparison : r.getPairwiseComparisons()) {
			double[] createConstraints = variableValue.clone();
			createConstraints[index] = 1.0;
			index++;
			variableName.add("z"+zNum);
			zNum++;
			lp.addConstraint(new LinearBiggerThanEqualsConstraint(createConstraints, 0.0, "c"+constraintNum));
			//System.out.println("C"+Arrays.toString(createConstraints));
			constraintNum++;
			for(Issue i : getIssues()) {
				int indexValueBid1 = variableName.indexOf("w"+i.getNumber()+"x"+comparison.getBid1().getValue(i).toString());
				int indexValueBid2 = variableName.indexOf("w"+i.getNumber()+"x"+comparison.getBid2().getValue(i).toString());
				createConstraints[indexValueBid1] = createConstraints[indexValueBid1] + -1.0;
				createConstraints[indexValueBid2] = createConstraints[indexValueBid2] + 1.0;
			}
			// z constraint + comparing constraint
			lp.addConstraint(new LinearBiggerThanEqualsConstraint(createConstraints, 0.0, "c"+constraintNum));
			//System.out.println("C"+Arrays.toString(createConstraints));
			constraintNum++;
		}
		
		//create bestBid constraint
		Bid bestBid = r.getMaximalBid();
		double[] createBestConstraints = variableValue.clone();
		for(Issue i : getIssues()) {
			int indexValueBid = variableName.indexOf("w"+i.getNumber()+"x"+bestBid.getValue(i).toString());
			createBestConstraints[indexValueBid] = 1.0;
		}
		lp.addConstraint(new LinearEqualsConstraint(createBestConstraints, 1.0, "c"+constraintNum));
		constraintNum++;
		
		System.out.println("Variable: "+variableXSize+" arraySize: "+variableValue.length+" constraintsNum: "+lp.getConstraints().size());
		lp.setMinProblem(true);
		LinearProgramSolver solver  = SolverFactory.newDefault();
		double[] sol = solver.solve(lp);
		return sol;
	}

	private void normalizeWeightsByMaxValues() {
		for (Issue i : getIssues()) {
			EvaluatorDiscrete evaluator = (EvaluatorDiscrete) u.getEvaluator(i);
			evaluator.normalizeAll();
		}
		scaleAllValuesFrom0To1();
		u.normalizeWeights();
	}

	public void scaleAllValuesFrom0To1() {
		for (Issue i : getIssues()) {
			EvaluatorDiscrete evaluator = (EvaluatorDiscrete) u.getEvaluator(i);
			evaluator.scaleAllValuesFrom0To1();
		}
	}

	public void normalizeWeights() {
		u.normalizeWeights();
	}

	public AdditiveUtilitySpace getUtilitySpace() {
		return u;
	}

	public List<IssueDiscrete> getIssues() {
		List<IssueDiscrete> issues = new ArrayList<>();
		for (Issue i : getDomain().getIssues()) {
			IssueDiscrete issue = (IssueDiscrete) i;
			issues.add(issue);
		}
		return issues;
	}

	public Domain getDomain() {
		return u.getDomain();
	}

}