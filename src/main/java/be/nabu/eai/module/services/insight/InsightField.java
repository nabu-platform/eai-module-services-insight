package be.nabu.eai.module.services.insight;

public class InsightField {
	// you can select something "as"
	private String alias;
	// the field you want to select, this can be a ":" separated one?
	private String key;
	// the aggregate function you want to use
	private String aggregate;
	
	public String getAlias() {
		return alias;
	}
	public void setAlias(String alias) {
		this.alias = alias;
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getAggregate() {
		return aggregate;
	}
	public void setAggregate(String aggregate) {
		this.aggregate = aggregate;
	}
}
