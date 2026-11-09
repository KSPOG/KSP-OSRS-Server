package net.runelite.client.plugins.microbot.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

final class MicrobotTourSequence
{
	private final List<MicrobotTourStep> steps;
	private final Predicate<MicrobotTourStep> available;
	private final Set<MicrobotTourStep> skipped = EnumSet.noneOf(MicrobotTourStep.class);

	MicrobotTourSequence(List<MicrobotTourStep> steps, Predicate<MicrobotTourStep> available)
	{
		this.steps = new ArrayList<>(steps);
		this.available = available;
	}

	Optional<MicrobotTourStep> first()
	{
		return steps.stream().filter(this::active).findFirst();
	}

	Optional<MicrobotTourStep> next(MicrobotTourStep step)
	{
		for (int i = steps.indexOf(step) + 1; i > 0 && i < steps.size(); i++)
		{
			if (active(steps.get(i)))
			{
				return Optional.of(steps.get(i));
			}
		}
		return Optional.empty();
	}

	Optional<MicrobotTourStep> previous(MicrobotTourStep step)
	{
		for (int i = steps.indexOf(step) - 1; i >= 0; i--)
		{
			if (active(steps.get(i)))
			{
				return Optional.of(steps.get(i));
			}
		}
		return Optional.empty();
	}

	int position(MicrobotTourStep step)
	{
		int index = steps.indexOf(step);
		int position = 1;
		for (int i = 0; i < index; i++)
		{
			if (active(steps.get(i)))
			{
				position++;
			}
		}
		return position;
	}

	int total()
	{
		return (int) steps.stream().filter(this::active).count();
	}

	boolean isLast(MicrobotTourStep step)
	{
		return next(step).isEmpty();
	}

	void markSkipped(MicrobotTourStep step)
	{
		skipped.add(step);
	}

	private boolean active(MicrobotTourStep step)
	{
		return !skipped.contains(step) && available.test(step);
	}
}
