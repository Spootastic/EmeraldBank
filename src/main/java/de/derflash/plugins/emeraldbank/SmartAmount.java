package de.derflash.plugins.emeraldbank;

public class SmartAmount {
	
	private int emeralds;
	private int emeraldBlocks;

	SmartAmount(int amount) {
		emeraldBlocks = (int)Math.floor(amount / 9);
		emeralds = amount % 9;
	}

	public int getEmeralds() {
		return emeralds;
	}

	public void setEmeralds(int emeralds) {
		this.emeralds = emeralds;
	}

	public int getEmeraldBlocks() {
		return emeraldBlocks;
	}

	public void setEmeraldBlocks(int emeraldBlocks) {
		this.emeraldBlocks = emeraldBlocks;
	}
	
	public int getAmount() {
		return emeraldBlocks * 9 + emeralds;
	}
	
	public String getAmountAsString() {
		String ausgezahltS = (getEmeralds() == 0 ? null : EmeraldBank.p.economy().format(getEmeralds()) );
		String ausgezahltP = (getEmeraldBlocks() == 0 ? null : (getEmeraldBlocks() == 1 ? "1 Emerald Block" : (getEmeraldBlocks() + " Emerald Block") ) );
		
		String ausgezahlt;
		if (ausgezahltS != null && ausgezahltP != null) ausgezahlt = ausgezahltP + " & " + ausgezahltS;
		else if (ausgezahltS != null) ausgezahlt = ausgezahltS;
		else ausgezahlt = ausgezahltP;
		
		return ausgezahlt;
	}
	
}
