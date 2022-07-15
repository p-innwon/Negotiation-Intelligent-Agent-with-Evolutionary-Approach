package project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import project.LinearProgrammingUtilitySpaceEstimator;
import project.JohnnyOpponentModel;


public class GAagent extends AbstractNegotiationParty {
    
	private double bidMaxUtil;
	//To keep preference
	private List<Bid> historySendingOffers = new ArrayList<Bid>();
	private int randomBid = 3;
    private Bid lastReceivedOffer;
    private List<Issue> issues;
    //For Opponent Model
  	private JohnnyOpponentModel opponentModel;
  	
	//For User uncertainly
	private UserModel userModel;
	//Genetic algorithm parameter
	private HashMap<Bid,Double> popList = new HashMap<Bid,Double>();
	
    private double alpha = 0.5;
    private double beta = 0.8;
	private int popSize = 100; // population size
	private int	selectionPool = 80; // mating pool size
	private double elitism = 0.1;
	private double crossoverRate = 0.6;
	private double mutationRate = 0.05;

    @Override
    public void init(NegotiationInfo info) {
    	//create new heuristic user model
    	userModel = info.getUserModel();
    	super.init(info);
    	AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
    	AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
    	
    	issues = additiveUtilitySpace.getDomain().getIssues();
    	
    	BidRanking bidRanking = userModel.getBidRanking();
    	int bidRankingSize = bidRanking.getSize();
    	List<Bid> bidList = new ArrayList<Bid>();
    	if(popSize < bidRankingSize) {
	    	bidList = bidRanking.getBidOrder().subList(bidRankingSize-popSize, bidRankingSize);
    	} else {
    		bidList = bidRanking.getBidOrder();
    	}
    	for(int i=0;i<bidList.size();i++) {
    		popList.put(bidList.get(i), 0.0);
    	}
    	
    	bidMaxUtil = getUtility(getMaxUtilityBid());
    	
    	//Init Opponent Model
    	opponentModel = new JohnnyOpponentModel(utilitySpace);
    			
    }
    
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		return utilitySpaceEstimator(getDomain(), userModel);
	}
	
	//self heuristic method
	public static AbstractUtilitySpace utilitySpaceEstimator(Domain domain, UserModel um)
	{
		LinearProgrammingUtilitySpaceEstimator myUtility = new LinearProgrammingUtilitySpaceEstimator(domain);
		BidRanking bidRanking = um.getBidRanking();
		myUtility.estimateUsingBidRanks(bidRanking);
		return myUtility.getUtilitySpace();
	}

    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {

        double time = getTimeLine().getTime();
        double timePressure = 1 - Math.pow(Math.min(time, 1)/1, 1/beta);
		if (lastReceivedOffer != null) {
			//Accept Bid if opponent bid utility is higher than its last offer
				
			if(getUtility(lastReceivedOffer)>= bidMaxUtil || historySendingOffers.contains(lastReceivedOffer)) {
				System.out.println("accepting offer");
				return new Accept(this.getPartyId(), lastReceivedOffer);
			} 
			//Doing genetic algorithm
			else {
				System.out.println("Creating offers w/ genetic algorithm");
				HashMap<Bid,Double> newPopList = new HashMap<Bid,Double>();
				
				//Evaluate offers
				
				for (Bid bid : popList.keySet()) {
					double fitness = getFitness(bid,lastReceivedOffer,timePressure);
					popList.put(bid,fitness);
				}
				
				//Elitism (sorting + choosing best bids by fitness)
				
				int numElit = (int) ((double) popList.size() * elitism);
				List<Entry<Bid, Double>> sortedList = new ArrayList<>(popList.entrySet());
				sortedList.sort(Entry.<Bid, Double>comparingByValue().reversed());
				sortedList = sortedList.subList(0, numElit);
		        for (Entry<Bid, Double> entry : sortedList) {
		        	newPopList.put(entry.getKey(),entry.getValue());
		        }
		        
				//Selection

				List<Bid> bidList = new ArrayList<Bid>(popList.keySet());
				List<Bid> selectBid = new ArrayList<Bid>();
				
				// if pop size is less than default pool size
				int matingPoolSize = selectionPool;
				if(popList.size() < selectionPool) {
					matingPoolSize = popList.size();
				}
				
				for(int i=0;i<matingPoolSize;i++) {
					Random rand1 = new Random();
					Random rand2 = new Random();

					Bid Bid1 = bidList.get(rand1.nextInt(bidList.size()));
					Bid Bid2 = bidList.get(rand2.nextInt(bidList.size()));
					if(popList.get(Bid1) > popList.get(Bid2)) {
						selectBid.add(Bid1);
					} else {
						selectBid.add(Bid2);
					}
				}
		
				//Crossover
		
				List<Bid> crossoverList = new ArrayList<Bid>();
				for(int i=0;i<matingPoolSize;i=i+2) {
					if(i >= matingPoolSize) {
						break;
					}
					Bid bid1 = bidList.get(i);
					int selectBid2 = i+1;
					if(selectBid2==bidList.size()) {
						selectBid2 = 0;
					}
					Bid bid2 = bidList.get(selectBid2);
					
					double rand = new Random().nextDouble();
					if(crossoverRate > rand) {					
						Random rand1 = new Random();
						Random rand2 = new Random();

						int start = rand1.nextInt(issues.size());
						int end = rand2.nextInt(issues.size());
						if(start > end) {
							int temp = start;
							start = end;
							end = temp;
						}
						
						for(int j = start;j<end+1;j++) {
							//swap value based on issue number
							Issue issue = issues.get(j);
							Value val1 = bid1.getValue(issue);
							Value val2 = bid2.getValue(issue);
							Bid newBid1 = bid1.putValue(issue.getNumber(), val2);
							Bid newBid2 = bid2.putValue(issue.getNumber(), val1);
							bid1 = newBid1;
							bid2 = newBid2;
						}
					}
					crossoverList.add(bid1);
					crossoverList.add(bid2);
				}
		        
				//Mutation
				
				List<Bid> mutationList = new ArrayList<Bid>();
				for(Bid bid: crossoverList) {
					for(Issue issue: issues) {
						double rand = new Random().nextDouble();
						if(mutationRate > rand) {
							IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
							Random randVal = new Random();
							Value newVal = issueDiscrete.getValue(randVal.nextInt(issueDiscrete.getNumberOfValues()));
							Bid newBid = bid.putValue(issue.getNumber(), newVal);
							bid = newBid;
						}
					}
					mutationList.add(bid);
				}	
				
				//Creating new pop
				//Mutation>crossover>selection non-dupe
				
				int currentPop = newPopList.size();
				for(Bid bid: mutationList) {
					if(!newPopList.containsKey(bid)) {
						double fitness = 0.0;
						if(popList.containsKey(bid)) {
							fitness = popList.get(bid);
						}
						newPopList.put(bid,fitness);
						currentPop++;
					}
					if(currentPop==popSize) {
						break;
					}
				}
				if(currentPop!=popSize) {
					Collections.shuffle(crossoverList);
					for(Bid bid: crossoverList) {
						if(!newPopList.containsKey(bid)) {
							double fitness = 0.0;
							if(popList.containsKey(bid)) {
								fitness = popList.get(bid);
							}
							newPopList.put(bid,fitness);
							currentPop++;
						}
						if(currentPop==popSize) {
							break;
						}
					}
					if(currentPop!=popSize) {
						Collections.shuffle(selectBid);
						for(Bid bid: selectBid) {
							if(!newPopList.containsKey(bid)) {
								newPopList.put(bid,popList.get(bid));
								currentPop++;
							}
							if(currentPop==popSize) {
								break;
							}
						}
					}
				}
				popList = newPopList;
				for (Entry<Bid, Double> entry : popList.entrySet()) {
					if(entry.getValue() == 0.0) {
						popList.put(entry.getKey(),getFitness(entry.getKey(),lastReceivedOffer,timePressure));
					}
				}
				//Deciding offer (+considering last opponent offer)
				
				List<Entry<Bid, Double>> decidedList = new ArrayList<>(popList.entrySet());
				decidedList.sort(Entry.<Bid, Double>comparingByValue().reversed());
				decidedList = decidedList.subList(0, randomBid);
				//choose top three offers in random
				Random rand = new Random();
				Bid bestBid = decidedList.get(rand.nextInt(randomBid)).getKey();
				double bestFitness = decidedList.get(rand.nextInt(randomBid)).getValue();
		        
				if(getUtility(bestBid) >= getUtility(lastReceivedOffer)) {
					//record proposed offers
					if(!historySendingOffers.contains(bestBid)) {
						historySendingOffers.add(bestBid);
					}
					System.out.println("send new offer <fitness="+bestFitness+",utility="+getUtility(bestBid)+">");
					return new Offer(getPartyId(), bestBid);
				} 
				else {
					System.out.println("accepting offer (comparing with proposal offers "+getUtility(bestBid)+" >= "+getUtility(lastReceivedOffer)+")");
				    return new Accept(getPartyId(), lastReceivedOffer);
				}
			}
		}
		System.out.println("no offering response");
		// first offer (no opponent offer)
		historySendingOffers.add(getMaxUtilityBid());
	    return new Offer(getPartyId(), getMaxUtilityBid());
    }

    public double getFitness(Bid offer,Bid opponentOffer,double TP) {
    	AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
    	
    	//calculate euclideanDistance
    	double euclideanDistance = 0.0;
    	for (Issue issue : issues) {
    		EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issue);
    		double issueWeightOwn = additiveUtilitySpace.getWeight(issue);
    		double attrValueOwn = evaluatorDiscrete.getDoubleValue((ValueDiscrete) offer.getValue(issue));
    		double attrValueOpp = evaluatorDiscrete.getDoubleValue((ValueDiscrete) opponentOffer.getValue(issue));
    		if(attrValueOwn!=attrValueOpp) {
    			double distance = issueWeightOwn * (attrValueOwn - attrValueOpp);
    			euclideanDistance = euclideanDistance + (distance*distance);
    		}
    	}
    	euclideanDistance = Math.sqrt(euclideanDistance);
    	
    	double constant = alpha*TP;
    	double ownUtility = getUtility(offer)/bidMaxUtil;
    	double nashPoint = getUtility(offer)*opponentModel.getOpponentUtility(offer);
    	double fitnessValue = constant*ownUtility+(1-constant)*((1-euclideanDistance))+nashPoint;
    	return fitnessValue;
    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) {
            Offer offer = (Offer) act;
            // storing last received offer
            lastReceivedOffer = offer.getBid();
            // update opponent model
         	opponentModel.updateOpponentPreference(lastReceivedOffer);
            
        }
    }


    @Override
    public String getDescription() {
        return "GAagent";
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}