package project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import genius.core.Bid;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

public class JohnnyOpponentModel {

	//Opponent Model
	//JohnyBlack
	private HashMap<String, HashMap<String, Integer>> opponentFrequency = new HashMap<String, HashMap<String, Integer>>();
	private HashMap<String, HashMap<String, Double>> opponentRank = new HashMap<String, HashMap<String, Double>>();
	private List<Double> opponentWeight = new ArrayList<Double>();
	private int opponentBidCount = 0;

	public JohnnyOpponentModel(AbstractUtilitySpace utilitySpace) {
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();

		for (Issue issue : issues) {
			HashMap<String, Integer> initFrequency = new HashMap<String, Integer>();
			HashMap<String, Double> initRank = new HashMap<String, Double>();
			opponentWeight.add(1.0 / (double) issues.size());
			
			IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	initFrequency.put(valueDiscrete.getValue(), 0);
		    	initRank.put(valueDiscrete.getValue(), 1.0);
			}
		    opponentFrequency.put(issue.getName(), initFrequency);
		    opponentRank.put(issue.getName(), initRank);
		}
	}
	
	public void updateOpponentPreference(Bid opponentBid) {
		
		//adding frequency
		opponentFrequency = addOpponentFrequency(opponentFrequency, opponentBid);
		
		//sorting
		opponentFrequency = sortOpponentFrequency(opponentFrequency);

		//ranking options & compute opponent issue weight
		double sumWeightIssue = 0;
		List<Double> weightIssueList = new ArrayList<Double>();
		for (Entry<String, HashMap<String, Integer>> issue : opponentFrequency.entrySet()) {
			HashMap<String, Integer> options = issue.getValue();
			int optionNum = options.size();
			int rank = 0;
			HashMap<String, Double> issueRank = new LinkedHashMap<>();
			
			double weightIssue = 0;
			int lastFrequency = 0;
			int sameRank = 0;
	        for (Entry<String, Integer> entry : options.entrySet()) {
	        	if(lastFrequency == entry.getValue()) {
	            	sameRank++;
	            }
	            else {
	            	rank = rank + 1 + sameRank;
	            	sameRank = 0;
	            	lastFrequency = entry.getValue();
	            }
	            double calculated_value = (double)(optionNum - rank + 1) / (double) optionNum;
	            issueRank.put(entry.getKey(), calculated_value);
	            
	            weightIssue = weightIssue + Math.pow((double) entry.getValue() / (double) opponentBidCount , 2);
	        }
	        //For Normalise
	        sumWeightIssue = sumWeightIssue + weightIssue;
	        weightIssueList.add(weightIssue);
	        opponentRank.put(issue.getKey().toString(), issueRank);
		}
		
		//Normalise value
		for (int i = 0; i < weightIssueList.size(); i++) {
			weightIssueList.set(i, weightIssueList.get(i) / sumWeightIssue);
		}
	}
	
	private HashMap<String, HashMap<String, Integer>> addOpponentFrequency(HashMap<String, HashMap<String, Integer>> opponentFrequency, Bid opponentBid) {
		for (Issue issue : opponentBid.getIssues()) {
			HashMap<String, Integer> frequency = opponentFrequency.get(issue.getName());
			frequency.put(opponentBid.getValue(issue).toString(), frequency.get(opponentBid.getValue(issue).toString()) + 1);
			opponentFrequency.put(issue.getName(), frequency);
		}
		//add count for compute item's weight
		opponentBidCount++;
		return opponentFrequency;
	}
	
	private HashMap<String, HashMap<String, Integer>> sortOpponentFrequency(HashMap<String, HashMap<String, Integer>> opponentFrequency) {
		//System.out.println("unsorted:" + opponentFrequency.toString());
		HashMap<String, HashMap<String, Integer>> sortFrequency = new HashMap<String, HashMap<String, Integer>>();
		for (Entry<String, HashMap<String, Integer>> issue : opponentFrequency.entrySet()) {
			Map<String, Integer> options = issue.getValue();
			List<Entry<String, Integer>> list = new ArrayList<>(options.entrySet());
			list.sort(Entry.<String, Integer>comparingByValue().reversed());
			
			HashMap<String, Integer> result = new LinkedHashMap<>();
	        for (Entry<String, Integer> entry : list) {
	            result.put(entry.getKey(), entry.getValue());
	        }
	        sortFrequency.put(issue.getKey().toString(), result);
		}
		return sortFrequency;
	}
	
	public double getOpponentUtility(Bid opponentBid) {
		double calculatedUtility = 0;
		int i = 0;
		for (Issue issue : opponentBid.getIssues()) {
			HashMap<String, Double> rank = opponentRank.get(issue.getName());
			calculatedUtility = calculatedUtility + (rank.get(opponentBid.getValue(issue).toString()) * opponentWeight.get(i));
			i++;
		}
		return calculatedUtility;
	}
	
	public HashMap<String, HashMap<String, Double>> getOpponentRank() {
		return opponentRank;
	}

	public List<Double> getOpponentWeight() {
		return opponentWeight;
	}

	public int getOpponentBidCount() {
		return opponentBidCount;
	}
}
