## MODIFIED Requirements

### Requirement: Rich help content
The manual SHALL include detailed sections for the sequencer, MIDI learn, and chorus effect that go beyond brief descriptions.

#### Scenario: Sequencer walkthrough
- **WHEN** user opens the manual's sequencer section
- **THEN** they see a step-by-step guide with examples (e.g., "Creating a bassline in 4 steps")

#### Scenario: MIDI learn guide
- **WHEN** user opens the manual's MIDI section
- **THEN** they see instructions on how to map MIDI controls using the learn workflow

#### Scenario: Chorus documentation
- **WHEN** user opens the manual's effects section
- **THEN** the chorus effect is documented with parameter descriptions

### Requirement: Search filters content
The manual search SHALL filter sections in real-time based on the search query.

#### Scenario: Search for "filter"
- **WHEN** user types "filter" in the search field
- **THEN** only sections containing "filter" in their title or content are shown
