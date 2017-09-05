NrtClock {
	var secs = 0;
	var time = 0;
	var queue, tempoQueue;

	var ptr;

	var <beatsPerBar=4.0, barsPerBeat=0.25;
	var <baseBarBeat=0.0, <baseBar=0.0;

	init { arg tempo, beats, seconds, queueSize;
		queue = PriorityQueue.new;
	}

	// |maxTime=60,padding=0|
	render {|maxTime=60|
		time = 0; beats = 0;
        while {queue.notEmpty and: {time<maxTime}} {
			while(tempoQueue.isEmpty.not
				&& secs2beats(tempoQueue.topPriority) <= queue.topPriority)
			{
				time = time + secs2beats(tempoQueue.topPriority);
				secs = secs + tempoQueue.topPriority;
				pr_tempo(tempoQueue.pop);
				this.changed(\tempo);
			}

			time = queue.topPriority;
			secs = secs + beats2secs(queue.topPriority);
            clock.beats = time;

            thisThread.clock = this;
            dt = item.awake(this.beats, this.time, clock);

            if(dt.isNumber) {
                clock.sched(dt, item);
            };
        };

        ^pScore;
	}

	tempo {
		^tempo;
	}
	beatDur {
		^beatDur;
	}
	elapsedBeats {
		^beats;
	}
	beats {
		^beats;
	}
	beats_ { arg beats;
		deltaSeconds = beats2secs(beats - this.beats);
		this.beats = beats;
		this.seconds = seconds + deltaSeconds;
		thisThread.seconds = this.seconds;
		thisThread.beats = this.beats;
	}

	seconds { ^thisThread.seconds }

    schedAbs {|t,item|
        queue.put(t,[item,this]);
    }

    sched {|dt, item|
        this.schedAbs(beats+(dt/tempo), item);
    }

	tempo_ { arg newTempo;
		this.setTempoAtBeat(newTempo, this.beats);
		this.changed(\tempo);  // this line is added
	}
	beatsPerBar_ { arg newBeatsPerBar;
		if (thisThread.clock != this) {
			"should only change beatsPerBar within the scheduling thread.".error;
			^this
		};
		this.setMeterAtBeat(newBeatsPerBar, thisThread.beats);
	}

	// for setting the tempo at the current elapsed time .
	etempo_ { arg newTempo;
		this.setTempoAtBeat(newTempo, this.beats);
		this.changed(\tempo);  // this line is added
	}

	//FIXME: tempoClock uses mBaseBeats and mBaseSeconds, perhaps something we also need to care about?
	secs2beats {arg secs;
	    var delta = secs - this.secs;
		^(this.time + (delta * tempo));
	}

	beats2secs {arg inBeats;
		var delta = inBeats - this.time;
		^(this.secs + (inBeats / tempo));
	}

	nextTimeOnGrid { arg quant = 1, phase = 0;
		if (quant == 0) { ^this.beats + phase };
		if (quant < 0) { quant = beatsPerBar * quant.neg };
		if (phase < 0) { phase = phase % quant };
		^roundUp(this.beats - baseBarBeat - (phase % quant), quant) + baseBarBeat + phase
	}

	timeToNextBeat { arg quant=1.0; // logical time to next beat
		^quant.nextTimeOnGrid(this) - this.beats
	}

	beats2bars { arg beats;
		^(beats - baseBarBeat) * barsPerBeat + baseBar;
	}
	bars2beats { arg bars;
		^(bars - baseBar) * beatsPerBar + baseBarBeat;
	}
	bar {
		// return the current bar.
		^this.beats2bars(this.beats).floor;
	}
	nextBar { arg beat;
		// given a number of beats, determine number beats at the next bar line.
		if (beat.isNil) { beat = this.beats };
		^this.bars2beats(this.beats2bars(beat).ceil);
	}
	beatInBar {
		// return the beat of the bar, range is 0 to < t.beatsPerBar
		^this.beats - this.bars2beats(this.bar)
	}

	pr_tempo {|t|
		tempo = t;
	}

	setTempoAtBeat { arg newTempo, beats;
		queue.put(t,Routine({thisThread.clock.pr_tempo(newTempo)}));
	}

	setTempoAtSec { arg newTempo, secs;
		tempoQueue.put(secs, newTempo);
	}

	// meter should only be changed in the TempoClock's thread.
	setMeterAtBeat { arg newBeatsPerBar, beats;
		// bar must be integer valued when meter changes or confusion results later.
		baseBar = round((beats - baseBarBeat) * barsPerBeat + baseBar, 1);
		baseBarBeat = beats;
		beatsPerBar = newBeatsPerBar;
		barsPerBeat = beatsPerBar.reciprocal;
		this.changed(\meter);
	}
}