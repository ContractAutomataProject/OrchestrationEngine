package io.github.contractautomata.RunnableOrchestration.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.contractautomata.RunnableOrchestration.AutoCloseableList;
import io.github.contractautomata.RunnableOrchestration.RunnableOrchestration;
import io.github.contractautomata.RunnableOrchestration.actions.OrchestratorAction;
import io.github.davidebasile.contractautomata.automaton.Automaton;
import io.github.davidebasile.contractautomata.automaton.MSCA;
import io.github.davidebasile.contractautomata.automaton.label.Label;
import io.github.davidebasile.contractautomata.automaton.state.BasicState;
import io.github.davidebasile.contractautomata.automaton.transition.MSCATransition;
import io.github.davidebasile.contractautomata.automaton.transition.Transition;

/**
 * each choice is solved by asking the services, and selecting the (or one of the) 
 * most frequent choice
 * 
 * @author Davide Basile
 *
 */
public class MajoritarianChoiceRunnableOrchestration extends RunnableOrchestration {
	

	public MajoritarianChoiceRunnableOrchestration(Automaton<String, String, BasicState, Transition<String, String, BasicState, Label<String>>> req,
			Predicate<MSCATransition> pred, List<MSCA> contracts, List<String> hosts, List<Integer> port, OrchestratorAction act) {
		super(req, pred, contracts, hosts, port, act);
	}

	@Override
	public String choice(AutoCloseableList<ObjectOutputStream> oout, AutoCloseableList<ObjectInputStream> oin)
			throws IOException, ClassNotFoundException {

		//computing services that can choose (those involved in one next transition)
		Set<Integer> toInvoke = this.getContract()
		.getForwardStar(this.getCurrentState()).stream()
		.flatMap(t->t.getLabel().isOffer()?Stream.of(t.getLabel().getOfferer())
				:Stream.of(t.getLabel().getOfferer(),t.getLabel().getRequester()))
		.distinct().collect(Collectors.toSet());
		
		//asking either to choose or skip to the services
		for (int i=0;i<oout.size();i++){
			ObjectOutputStream oos = oout.get(i);
			oos.writeObject(toInvoke.contains(i)?RunnableOrchestration.choice_msg:null);
			oout.get(i).flush();
		}
		
		//computing and sending the possible choices
		String[] toChoose = this.getContract()
				.getForwardStar(this.getCurrentState()).stream()
				.map(t->t.getLabel().getUnsignedAction())
				.toArray(String[]::new);
		for (Integer i : toInvoke) {
			oout.get(i).writeObject(toChoose);
			oout.get(i).flush();
		}
		
		//receiving the choice of each service
		List<String> choices = new ArrayList<>(); 
		for (int i=0;i<oin.size();i++)
			choices.add((String) oin.get(i).readObject());
		
		return	choices.stream()
		.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
		.entrySet().stream()
		.max((x,y)->x.getValue().intValue()-y.getValue().intValue()).orElseThrow(RuntimeException::new).getKey();
		
	}

	

}
