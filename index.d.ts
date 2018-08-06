declare class BioPass {
  static store(password: string): Promise<void>
  static retreive(prompt: string): Promise<string>
  static delete(): Promise<void>
}

export default BioPass
